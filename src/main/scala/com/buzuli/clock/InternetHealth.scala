package com.buzuli.clock

import java.time.Instant
import java.util.concurrent.TimeUnit

import com.buzuli.util.{Async, Http, HttpResult, HttpResultInvalidBody, HttpResultInvalidHeader, HttpResultInvalidMethod, HttpResultInvalidUrl, HttpResultRawResponse, Scheduled, Scheduler, Time}
import com.pi4j.io.gpio.{Pin, PinMode, PinState, RaspiGpioProvider, RaspiPin, RaspiPinNumberingScheme}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

sealed trait Outage
case object InternetOutage extends Outage
case class InternetServiceOutage(service: String) extends Outage
case object LocalNetworkOutage extends Outage
case class LocalServiceOutage(service: String) extends Outage

class InternetHealth {
  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  private lazy val scheduler: Scheduler = Scheduler.create()
  private var scheduled: Option[Scheduled] = None

  private var offlineSince: Option[Instant] = None
  private var lastReset: Option[Instant] = None
  private lazy val gpio: Option[RaspiGpioProvider] = Some(
    new RaspiGpioProvider(RaspiPinNumberingScheme.BROADCOM_PIN_NUMBERING)
  )
  private val gpioPin: Option[Pin] = Config.internetResetPin map { pinAddress =>
    val pin: Pin = RaspiPin.getPinByAddress(pinAddress)
    gpio.foreach { g =>
      powerOn()
      g.`export`(pin, PinMode.DIGITAL_OUTPUT)
    }
    pin
  }

  def setHigh(): Unit = gpioPin foreach { pin =>
    gpio.foreach { _.setState(pin, PinState.HIGH) }
  }
  def setLow(): Unit = gpioPin foreach { pin =>
    gpio.foreach { _.setState(pin, PinState.LOW) }
  }

  def powerOn(): Unit = Config.internetResetNc match {
    case true => setLow()
    case false => setHigh()
  }
  def powerOff(): Unit = Config.internetResetNc match {
    case true => setHigh()
    case false => setLow()
  }

  def resetInternet(): Unit = {
    gpioPin.foreach { _ =>
      println("Cutting router power ...")
      lastReset = Some(Time.now)
      powerOff()
      Async.delay(1000) andThen { case _ =>
        println("Restoring router power ...")
        powerOn()
      }
    }
  }

  def start(): Unit = {
    if (scheduled.isEmpty) {
      println("Scheduling regular Internet health checks ...")

      scheduled = Some(scheduler.runEvery(
        Config.internetCheckInterval,
        startImmediately = true
      ) { Try {
        Await.result(checkHealth(), Duration(45, TimeUnit.SECONDS))
      } match {
        case Failure(error) => {
          println(s"Error in the Internet health-check system: ${error}")
          error.printStackTrace()
        }
        case Success(Some(LocalNetworkOutage)) => println("No local network connectivity.")
        case Success(Some(InternetOutage)) => {
          offlineSince = offlineSince match {
            case Some(whence) => {
              println(s"No Internet connectivity since ${whence}.")
              val sufficientOfflineDuration = Time.since(whence).gteq(Duration(5, TimeUnit.MINUTES))
              val sufficientResetDelay = lastReset match {
                case None => true
                case Some(whence) if Time.since(whence).gteq(Config.internetOutageDuration) => true
                case _ => false
              }
              if (sufficientOfflineDuration && sufficientResetDelay) {
                // Only rest if we have been offline for 5 minutes and
                // we haven't attempted a reset in the last 5 minutes.
                println("Internet reset needed.")
                resetInternet()
              }
              Some(whence)
            }
            case None => {
              println(s"Internet connectivity lost.")
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
              println(s"Internet connectivity restored after ${Time.prettyDuration(elapsed)}.")
              Notify.slack(s"Internet connectivity restored after ${Time.prettyDuration(elapsed)}")
              offlineSince = None
            }
          }
          otherOutage match {
            case None => println("Internet and local network are healthy.")
            case Some(LocalServiceOutage(service)) => println(s"Local service '${service}' is unavailable")
            case Some(InternetServiceOutage(service)) => println(s"Internet service '${service}' is unavailable.")
          }
        }
      } })
    }
  }

  def shutdown(): Unit = {
    println("Halting Internet health checks ...")

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
    checkService("vision", "http://vision:1337/health-check")
  }

  private def checkService(service: String, url: String): Future[Either[String, String]] = {
    Http.get(url) andThen {
      handleRequestResult(service)(_)
    } map { _ => Right(service) } recover { case _ => Left(service) }
  }

  def handleRequestResult(service: String)(result: Try[HttpResult]): Unit = result match {
    case Failure(error) => {
      println(s"Error contacting $service: $error")
      error.printStackTrace()
    }
    case Success(HttpResultInvalidMethod(method)) => {
      println(s"""Invalid method "$method" while checking service $service""")
    }
    case Success(HttpResultInvalidUrl(url)) => {
      println(s"""Invalid URL "$url" while checking service $service""")
    }
    case Success(HttpResultInvalidHeader(name, value)) => {
      println(s"""Invalid header "$name=$value" while checking service $service""")
    }
    case Success(HttpResultInvalidBody()) => {
      println(s"""Invalid message body while checking service $service""")
    }
    case Success(HttpResultRawResponse(response, _, duration)) => {
      val durationString = duration match {
        case Some(elapsed) => s" (took ${Time.prettyDuration(elapsed)})"
        case None => ""
      }
      println(s"""Status ${response.status} from service ${service}${durationString}""")
    }
  }
}
