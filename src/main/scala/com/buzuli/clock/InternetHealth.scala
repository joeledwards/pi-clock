package com.buzuli.clock

import java.util.concurrent.TimeUnit

import com.buzuli.util.Http

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

sealed trait Outage
case object InternetOutage extends Outage
case object InternetServiceOutage extends Outage
case object LocalNetworkOutage extends Outage
case object LocalServiceOutage extends Outage

class InternetHealth {
  private lazy val scheduler: Scheduler = Scheduler.create()
  private var scheduled: Option[Scheduled] = None

  def start(): Unit = {
    if (scheduled.isEmpty) {
      println("Scheduling regular Internet health checks ...")

      scheduled = Some(scheduler.runEvery(
        Duration(1, TimeUnit.MINUTES),
        startImmediately = true
      ) { Try {
        Await.ready(checkHealth(), Duration(45, TimeUnit.SECONDS))
      } match {
        case Success(_) =>
        case Failure(error) => {
          println(s"Error checking Internet health: ${error}")
          error.printStackTrace()
          // TODO: notify Slack (if available)
        }
      } })
    }
  }

  def shutdown(): Unit = {
    println("Halting Internet health checks ...")

    scheduled.foreach(_.cancel(true))
    scheduled = None
  }

  private def checkHealth(): Future[Unit] = {
    // Check access to two local servers
    // Check access to two Internet services

  }

  private def checkSlack(): Future[Boolean] = {
  }

  private def checkIp(): Future[Boolean] = {

  }

  private def checkNebula(): Future[Boolean] = {

  }

  private def checkHulk(): Future[Boolean] = {

  }
}
