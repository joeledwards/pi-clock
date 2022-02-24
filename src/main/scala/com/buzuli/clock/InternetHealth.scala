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
case class InternetServiceOutage(service: String) extends Outage
case object LocalNetworkOutage extends Outage
case class LocalServiceOutage(service: String) extends Outage

class InternetHealth extends LazyLogging {
  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  private lazy val scheduler: Scheduler = Scheduler.create()
  private var scheduled: Option[Scheduled] = None

  private var offlineSince: Option[Instant] = None
  private var lastReset: Option[Instant] = None
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

  def powerOn(): Unit = Config.internetPowerHigh match {
    case true => setHigh()
    case false => setLow()
  }

  def powerOff(): Unit = Config.internetPowerHigh match {
    case true => setLow()
    case false => setHigh()
  }

  def resetInternet(): Unit = {
    gpioPin.foreach { _ =>
      lastReset = Some(Time.now)

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

  def start(): Unit = {
    if (scheduled.isEmpty) {
      logger.info("Scheduling regular Internet health checks ...")

      scheduled = Some(
        scheduler.runEvery(
          Config.internetCheckInterval,
          startImmediately = true
        ) {
          Try {
            Await.result(checkHealth(), Config.internetCheckInterval * 0.75)
          } match {
            case Failure(error) => {
              logger.error(s"Error in the Internet health-check system", error)
            }
            case Success(Some(LocalNetworkOutage)) => logger.warn("No local network connectivity.")
            case Success(Some(InternetOutage)) => {
              offlineSince = offlineSince match {
                case Some(whence) => {
                  logger.warn(s"No Internet connectivity since ${whence} (${Time.prettyDuration(Time.since(whence))}).")
                  val sufficientOfflineDuration = Time.since(whence).gteq(Config.internetOutageDuration)
                  val sufficientResetDelay = lastReset match {
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
            case Success(otherOutage) => {
              offlineSince match {
                case None =>
                case Some(whence) => {
                  val elapsed = Duration.fromNanos(
                    java.time.Duration.between(whence, Instant.now).toNanos
                  )
                  logger.info(s"Internet connectivity restored after ${Time.prettyDuration(elapsed)}.")
                  Notify.slack(s"Internet connectivity restored after ${Time.prettyDuration(elapsed)}")
                  offlineSince = None
                }
              }
              otherOutage match {
                case None => logger.info("Internet and local network are healthy.")
                case Some(LocalServiceOutage(service)) => logger.warn(s"Local service '${service}' is unavailable")
                case Some(InternetServiceOutage(service)) => logger.warn(s"Internet service '${service}' is unavailable.")
                case Some(InternetOutage) => // Handled above
                case Some(LocalNetworkOutage) => // Handled above
              }
            }
          }
        }
      )
    }
  }

  def shutdown(): Unit = {
    logger.info("Halting Internet health checks ...")

    scheduled.foreach(_.cancel(true))
    scheduled = None
  }

  private def checkHealth(): Future[Option[Outage]] = {
    // Check access to two local servers
    // Check access to two Internet services
    val outcomes: List[Future[Either[String, String]]] = checkHttpbin() ::
      checkIp() ::
      checkVision() ::
      checkNebula() ::
      Nil

    val result = Future.sequence(
      outcomes.map(
        _.map(Success(_)).recover {
          case x => Failure(x)
        }
      )
    ) map {
      _.map {
        case Success(Left(service)) => Some(service)
        case Success(Right(_)) => None
        case _ => Some("unknown")
      }
    } map { _ match {
      case _ :: _ :: Some(_) :: Some(_) :: Nil => Some(LocalNetworkOutage)
      case Some(_) :: Some(_) :: _ :: _ :: Nil => Some(InternetOutage)
      case _ :: _ :: Some(service) :: None :: Nil => Some(LocalServiceOutage(service))
      case _ :: _ :: None :: Some(service) :: Nil => Some(LocalServiceOutage(service))
      case Some(service) :: None :: _ :: _ :: Nil => Some(InternetServiceOutage(service))
      case None :: Some(service) :: _ :: _ :: Nil => Some(InternetServiceOutage(service))
      case _ => None
    } }

    result
  }

  private def checkHttpbin() = {
    checkService("httpbin", "http://httpbin.org/")
  }
  private def checkIp() = {
    checkService("ip-api", "http://ip-api.com/json")
  }
  private def checkNebula() = {
    checkService("nebula", "http://nebula:1337/health-check")
  }
  private def checkVision() = {
    checkService("ironman", "http://ironman:1337/health-check")
  }

  private def checkService(service: String, url: String): Future[Either[String, String]] = {
    Http.get(url) andThen {
      handleRequestResult(service)(_)
    } map { _ => Right(service) } recover { case _ => Left(service) }
  }

  def handleRequestResult(service: String)(result: Try[HttpResult]): Unit = result match {
    case Failure(error) => {
      logger.error(s"Error contacting $service", error)
    }
    case Success(HttpResultInvalidMethod(method)) => {
      logger.warn(s"""Invalid method "$method" while checking service $service""")
    }
    case Success(HttpResultInvalidUrl(url)) => {
      logger.warn(s"""Invalid URL "$url" while checking service $service""")
    }
    case Success(HttpResultInvalidHeader(name, value)) => {
      logger.warn(s"""Invalid header "$name=$value" while checking service $service""")
    }
    case Success(HttpResultInvalidBody()) => {
      logger.warn(s"""Invalid message body while checking service $service""")
    }
    case Success(HttpResultRawResponse(response, _, duration)) => {
      val durationString = duration match {
        case Some(elapsed) => s" (took ${Time.prettyDuration(elapsed)})"
        case None => ""
      }
      logger.info(s"""Status ${response.code.code} from service ${service}${durationString}""")
    }
  }
}
