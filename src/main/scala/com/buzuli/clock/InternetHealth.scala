package com.buzuli.clock

import java.util.concurrent.TimeUnit

import akka.dispatch.Futures
import com.buzuli.util.{Http, HttpBodyJson}
import play.api.libs.json.Json

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

sealed trait Outage
case object InternetOutage extends Outage
case object InternetServiceOutage extends Outage
case object LocalNetworkOutage extends Outage
case object LocalServiceOutage extends Outage

class InternetHealth {
  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  private lazy val scheduler: Scheduler = Scheduler.create()
  private var scheduled: Option[Scheduled] = None

  def start(): Unit = {
    if (scheduled.isEmpty) {
      println("Scheduling regular Internet health checks ...")

      scheduled = Some(scheduler.runEvery(
        Duration(1, TimeUnit.MINUTES),
        startImmediately = true
      ) { Try {
        Await.result(checkHealth(), Duration(45, TimeUnit.SECONDS))
      } match {
        case Success(None) => println("Internet and network healthy.")
        case Success(Some(InternetOutage)) => println("No Internet connectivity.")
        case Success(Some(InternetServiceOutage)) => println("An Internet service is unavailable.")
        case Success(Some(LocalNetworkOutage)) => println("No local network connectivity.")
        case Success(Some(LocalServiceOutage)) => println("A local service is unavailable")
        case Failure(error) => {
          println(s"Error in the Internet health-check system: ${error}")
          error.printStackTrace()
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
    val outcomes: List[Future[Boolean]] = checkSlack() ::
      checkIp() ::
      checkHulk() ::
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
        case Success(true) => true
        case _ => false
      }
    } map { _ match {
      case _ :: _ :: false :: false :: Nil => Some(LocalNetworkOutage)
      case false :: false :: _ :: _ :: Nil => Some(InternetOutage)
      case _ :: _ :: false :: true :: Nil => Some(LocalServiceOutage)
      case _ :: _ :: true :: false :: Nil => Some(LocalServiceOutage)
      case false :: true :: _ :: _ :: Nil => Some(InternetServiceOutage)
      case true :: false :: _ :: _ :: Nil => Some(InternetServiceOutage)
      case _ => None
    } }

    result
  }

  private def checkSlack(): Future[Boolean] = {
    Config.healthSlackWebhook match {
      case None => Future.successful(false)
      case Some(webhook) => Http.post(webhook, body = Some(HttpBodyJson(Json.obj(
        "message" -> "health-check"
      )))) andThen {
        case Failure(error) => {
          println(s"Error sending Slack message: ${error}")
          error.printStackTrace()
        }
      } map { _ => true } recover { case _ => false }
    }
  }

  private def checkIp(): Future[Boolean] = {
    val ipUrl = "http://ip-api.com/json"
    Http.get(ipUrl) andThen {
      case Failure(error) => {
        println(s"Error contacting IP service: ${error}")
        error.printStackTrace()
      }
    } map { _ => true } recover { case _ => false }
  }

  private def checkNebula(): Future[Boolean] = {
    val nebulaUrl = "http://nebula:1337/health-check"
    Http.get(nebulaUrl) andThen {
      case Failure(error) => {
        println(s"Error contacting nebula: ${error}")
        error.printStackTrace()
      }
    } map { _ => true } recover { case _ => false }
  }

  private def checkHulk(): Future[Boolean] = {
    val hulkUrl = "http://hulk:1337/health-check"
    Http.get(hulkUrl) andThen {
      case Failure(error) => {
        println(s"Error contacting hulk: ${error}")
        error.printStackTrace()
      }
    } map { _ => true } recover { case _ => false }
  }
}
