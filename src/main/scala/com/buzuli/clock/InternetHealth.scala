package com.buzuli.clock

import java.time.Instant
import com.buzuli.util.{Http, HttpResult, HttpResultInvalidBody, HttpResultInvalidHeader, HttpResultInvalidMethod, HttpResultInvalidUrl, HttpResultRawResponse, Scheduled, Scheduler, Time}
import com.pi4j.context.Context
import com.pi4j.io.gpio.digital.{DigitalOutput, DigitalState}
import com.typesafe.scalalogging.LazyLogging

import java.util.concurrent.TimeUnit
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

sealed trait Outage
case object InternetOutage extends Outage
case object LocalNetworkOutage extends Outage

class InternetHealth(pi4jContext: Context) extends LazyLogging {
  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  private lazy val scheduler: Scheduler = Scheduler.create()
  private var scheduled: Option[Scheduled] = None

  private var networkDownSince: Option[Instant] = None
  private var internetDownSince: Option[Instant] = None
  private var lastPowerCycleTime: Option[Instant] = None

  private val resetPin: Option[DigitalOutput] = Config.internetPowerPin flatMap { pinAddress =>
    val pin = Try {
      DigitalOutput
        .newBuilder(pi4jContext)
        .name("Internet Reset Pin")
        .id("internet-reset-pin")
        .address(pinAddress)
        .build
    } match {
      case Success(p) => {
        logger.info(s"Initializing the Internet reset pin: ${p}")
        Scheduler.default.runAfter(1.second) {
          Config.internetPowerHigh match {
            case true => p.high()
            case false => p.low()
          }
        }
        Some(p)
      }
      case Failure(_) => {
        logger.info(s"No valid reset pin supplied: ${pinAddress}")
        None
      }
    }
    pin
  }

  def isNetworkDown: Boolean = networkDownSince.nonEmpty
  def isInternetDown: Boolean = internetDownSince.nonEmpty

  def setHigh(): Unit = resetPin foreach { pin =>
    Try {
      pin.high()
    } match {
      case Success(_) =>
      case Failure(error) => {
        logger.error(s"Error pulling pin ${pin} high", error)
      }
    }
  }

  def setLow(): Unit = resetPin foreach { pin =>
    Try {
      pin.low()
    } match {
      case Success(_) =>
      case Failure(error) =>
        logger.error(s"Error pulling pin ${pin} low", error)
    }
  }

  def resetPower(): Unit = resetPin foreach { pin =>
    val pulseDirection = if (Config.internetPowerHigh) DigitalState.LOW else DigitalState.HIGH
    pin.pulse(1, TimeUnit.SECONDS, pulseDirection)
  }

  def powerOn(): Unit = if (Config.internetPowerHigh) setHigh() else setLow()

  def powerOff(): Unit = if (Config.internetPowerHigh) setLow() else setHigh()

  def resetInternet(): Unit = {
    logger.info("Pulsing Internet power to reset ...")
    resetPower()
    logger.info("Internet reset.")
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

      // The health evaluation can be performed much more frequently
      // than the actual health check requests
      scheduled = Some(
        scheduler.runEvery(
          Duration(1, TimeUnit.SECONDS),
          startImmediately = true
        ) {
          run()
        }
      )
    }
  }

  private var lastStatus: Option[Instant] = None
  def logStatus(message: String, warn: Boolean = false, optional: Boolean = false): Unit = {
    if (!optional || lastStatus.exists(_.plusSeconds(60).isBefore(Instant.now))) {
      lastStatus = Some(Instant.now)

      if (warn) {
        logger.warn(message)
      } else {
        logger.info(message)
      }
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
            logStatus("Disconnected from local network.", warn = true)
            Some(Instant.now)
          }
          case ds @ Some(whence) => {
            logStatus(s"Local network down since ${whence}.", warn = true, optional = true)
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
            logStatus(s"Local network re-connected after ${Time.prettyDuration(elapsed)}")
            networkDownSince = None
          }
        }

        internetDownSince = internetDownSince match {
          case Some(whence) => {
            logStatus(
             s"No Internet connectivity since ${whence} (${Time.prettyDuration(Time.since(whence))}).",
              warn = true,
              optional = true
            )
            val sufficientOfflineDuration = Time.since(whence).gteq(Config.internetOutageDuration)
            val sufficientResetDelay = lastPowerCycleTime match {
              case None => true
              case Some(whence) if Time.since(whence).gteq(Config.internetResetDelay) => true
              case _ => false
            }
            if (sufficientOfflineDuration && sufficientResetDelay) {
              // Only rest if we have been offline for 5 minutes and
              // we haven't attempted a reset in the last 5 minutes.
              logStatus("Internet reset needed.")
              resetInternet()
            }
            Some(whence)
          }
          case None => {
            logStatus(s"Internet connectivity lost.", warn = true)
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
            logStatus(s"Internet connectivity restored after ${Time.prettyDuration(elapsed)}.")
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
            logStatus(s"Local network re-connected after ${Time.prettyDuration(elapsed)}")
            networkDownSince = None
          }
        }

        logStatus("Internet and local network are healthy.", optional = true)
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
