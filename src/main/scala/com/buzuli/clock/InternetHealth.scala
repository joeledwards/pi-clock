package com.buzuli.clock

import java.time.Instant
import com.buzuli.util.{Http, HttpResult, HttpResultInvalidBody, HttpResultInvalidHeader, HttpResultInvalidMethod, HttpResultInvalidUrl, HttpResultRawResponse, Scheduled, Scheduler, Time}
import com.pi4j.io.gpio.{Pin, PinMode, PinState, RaspiGpioProvider, RaspiPin, RaspiPinNumberingScheme}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

sealed trait Outage
case object InternetOutage extends Outage
case object LocalNetworkOutage extends Outage

class InternetHealth extends LazyLogging {
  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  private lazy val scheduler: Scheduler = Scheduler.create()
  private var scheduled: Option[Scheduled] = None

  private var networkDownSince: Option[Instant] = None
  private var internetDownSince: Option[Instant] = None
  private var lastPowerCycleTime: Option[Instant] = None

  private lazy val gpio: Option[RaspiGpioProvider] = {
    Some(new RaspiGpioProvider(
      RaspiPinNumberingScheme.BROADCOM_PIN_NUMBERING
    ))
  }
  private val gpioPin: Option[Pin] = Config.internetPowerPin flatMap { pinAddress =>
    Option(RaspiPin.getPinByAddress(pinAddress)) match {
      case None => {
        logger.info(s"No valid reset pin supplied: ${pinAddress}")
        None
      }
      case Some(pin) => {
        logger.info(s"Initializing the Internet reset pin: ${pin}")
        Scheduler.default.runAfter(1.second) {
          // This must be run after gpioPin has been assigned a value
          gpio.foreach { g =>
            powerOn()
            g.`export`(
              pin,
              PinMode.DIGITAL_OUTPUT,
              if (Config.internetPowerHigh) PinState.HIGH else PinState.LOW
            )
          }
        }
        Some(pin)
      }
    }
  }

  def isNetworkDown: Boolean = networkDownSince.nonEmpty
  def isInternetDown: Boolean = internetDownSince.nonEmpty

  def setHigh(): Unit = gpioPin foreach { pin =>
    Try {
      gpio.foreach { _.setState(pin, PinState.HIGH) }
    } match {
      case Success(_) =>
      case Failure(error) => {
        logger.error(s"Error pulling pin ${pin} high", error)
      }
    }
  }

  def setLow(): Unit = gpioPin foreach { pin =>
    Try {
      gpio.foreach { _.setState(pin, PinState.LOW) }
    } match {
      case Success(_) =>
      case Failure(error) =>
        logger.error(s"Error pulling pin ${pin} low", error)
    }
  }

  def powerOn(): Unit = if (Config.internetPowerHigh) setHigh() else setLow()

  def powerOff(): Unit = if (Config.internetPowerHigh) setLow() else setHigh()

  def resetInternet(): Unit = {
    gpioPin.foreach { _ =>
      lastPowerCycleTime = Some(Time.now)

      logger.info("Cutting Internet power ...")
      powerOff()
      logger.info("Power is off.")

      Scheduler.default.runAfter(1.second) {
        logger.info("Restoring Internet power ...")
        powerOn()
        logger.info("Power is on.")
      }
    }
  }

  val monitors: List[ServiceMonitor] = {
    val timeout = Config.internetCheckTimeout
    val delay = Config.internetCheckInterval

    List(
      new ServiceMonitor("httpbin", "http://httpbin.org/", timeout, delay, true),
      new ServiceMonitor("ip-api", "http://ip-api.com/json", timeout, delay, true),
      new ServiceMonitor("nebula", "http://nebula:1337/health-check", timeout, delay, false),
      new ServiceMonitor("ironman", "http://ironman:1337/health-check", timeout, delay, false)
    )
  }

  def start(): Unit = {
    monitors.foreach(_.start())

    if (scheduled.isEmpty) {
      logger.info("Scheduling regular Internet health checks ...")

      scheduled = Some(
        scheduler.runEvery(
          Config.internetCheckInterval,
          startImmediately = true
        ) {
          run()
        }
      )
    }
  }

  def run(): Unit = {
    checkHealth() match {
      case Some(LocalNetworkOutage) => {
        if (internetDownSince.nonEmpty) {
          internetDownSince = None
        }

        networkDownSince = networkDownSince match {
          case None => {
            logger.warn("Disconnected from local network.")
            Some(Instant.now)
          }
          case ds @ Some(whence) => {
            logger.warn(s"Local network down since ${whence}.")
            ds
          }
        }
      }
      case Some(InternetOutage) => {
        networkDownSince match {
          case None =>
          case Some(whence) => {
            val elapsed = Duration.fromNanos(
              java.time.Duration.between(whence, Instant.now).toNanos
            )
            logger.info(s"Local network re-connected after ${Time.prettyDuration(elapsed)}")
            networkDownSince = None
          }
        }

        internetDownSince = internetDownSince match {
          case Some(whence) => {
            logger.warn(s"No Internet connectivity since ${whence} (${Time.prettyDuration(Time.since(whence))}).")
            val sufficientOfflineDuration = Time.since(whence).gteq(Config.internetOutageDuration)
            val sufficientResetDelay = lastPowerCycleTime match {
              case None => true
              case Some(whence) if Time.since(whence).gteq(Config.internetResetDelay) => true
              case _ => false
            }
            if (sufficientOfflineDuration && sufficientResetDelay) {
              // Only rest if we have been offline for 5 minutes and
              // we haven't attempted a reset in the last 5 minutes.
              logger.info("Internet reset needed.")
              resetInternet()
            }
            Some(whence)
          }
          case None => {
            logger.warn(s"Internet connectivity lost.")
            Some(Instant.now)
          }
        }
      }
      case None => {
        internetDownSince match {
          case None =>
          case Some(whence) => {
            val elapsed = Duration.fromNanos(
              java.time.Duration.between(whence, Instant.now).toNanos
            )
            logger.info(s"Internet connectivity restored after ${Time.prettyDuration(elapsed)}.")
            Notify.slack(s"Internet connectivity restored after ${Time.prettyDuration(elapsed)}")
            internetDownSince = None
          }
        }

        networkDownSince match {
          case None =>
          case Some(whence) => {
            val elapsed = Duration.fromNanos(
              java.time.Duration.between(whence, Instant.now).toNanos
            )
            logger.info(s"Local network re-connected after ${Time.prettyDuration(elapsed)}")
            networkDownSince = None
          }
        }

        logger.info("Internet and local network are healthy.")
      }
    }
  }

  private def checkHealth(): Option[Outage] = {
    val internetMonitors = monitors.filter(_.isInternet)
    val networkMonitors = monitors.filter(!_.isInternet)

    val result: Option[Outage] = {
      if (!networkMonitors.exists(_.isConnected)) {
        Some(LocalNetworkOutage)
      } else if (!internetMonitors.exists(_.isConnected)) {
        Some(InternetOutage)
      } else {
        None
      }
    }

    result
  }


  def shutdown(): Unit = {
    logger.info("Halting Internet health checks ...")

    monitors.foreach(_.shutdown())

    scheduled.foreach(_.cancel(true))
    scheduled = None
  }
}
