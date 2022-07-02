package com.buzuli.clock

import com.buzuli.util.{Http, HttpResult, HttpResultInvalidBody, HttpResultInvalidHeader, HttpResultInvalidMethod, HttpResultInvalidUrl, HttpResultRawResponse, Scheduled, Scheduler, Time}
import com.typesafe.scalalogging.LazyLogging

import java.time.Instant
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class ServiceMonitor(
  name: String,
  url: String,
  timeout: Duration,
  minDelay: Duration,
  internet: Boolean
) extends LazyLogging {
  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  private var running = false
  private var scheduled: Option[Scheduled] = None
  private var disconnectedSince: Option[Instant] = None

  def isConnected: Boolean = disconnectedSince.isEmpty
  def isInternet: Boolean = internet

  def start(): Unit = {
    running = true
    run()
  }

  def shutdown(): Unit = {
    running = false
    scheduled.foreach(_.cancel(true))
    scheduled = None
  }

  def run(): Unit = {
    scheduled = None

    if (running) {
      val start = System.nanoTime()
      checkService() andThen {
        case _ => {
          val end = System.nanoTime()
          val elapsed = end - start
          val delay = Duration.fromNanos(Math.max(0, minDelay.toNanos - elapsed))
          scheduled = Some(Scheduler.default.runAfter(delay) {
            run()
          })
        }
      }
    }
  }

  private def checkService(): Future[Unit] = {
    Http.get(url, timeout = timeout) andThen {
      logRequestResult(name)(_)
    } map { _ =>
      disconnectedSince.foreach { whence =>
        val disconnectDuration = Duration.fromNanos((System.currentTimeMillis - whence.toEpochMilli) * 1000000)
        logger.info(s"Service '${name}' re-connected after ${Time.prettyDuration(disconnectDuration)}")
      }
      disconnectedSince = None
      ()
    } recover { case _ =>
      disconnectedSince match {
        case None => {
          logger.info(s"Service '${name}' disconnected")
          disconnectedSince = Some(Instant.now)
        }
        case Some(whence) => {
          val disconnectDuration = Duration.fromNanos((System.currentTimeMillis - whence.toEpochMilli) * 1000000)
          logger.info(s"Service '${name}' disconnected for ${Time.prettyDuration(disconnectDuration)}")
        }
      }
      ()
    }
  }

  def logRequestResult(service: String)(result: Try[HttpResult]): Unit = result match {
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
