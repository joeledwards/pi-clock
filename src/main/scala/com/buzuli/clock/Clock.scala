package com.buzuli.clock

import java.time.temporal.ChronoUnit
import java.time.Instant
import java.util.concurrent.TimeUnit
import com.buzuli.util.{Scheduled, Scheduler}

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

class Clock {
  type TickListener = (Instant) => Unit

  private var scheduled: Option[Scheduled] = None
  private var listeners: List[TickListener] = Nil
  private val scheduler = new Scheduler

  def start(): Unit = {
    if (scheduled.isEmpty) {
      scheduled = Some(
        scheduler.runAt(
          Instant
            .now
            .plusSeconds(1)
            .truncatedTo(ChronoUnit.SECONDS)
        ) {
          scheduled = Some(
            scheduler.runEvery(
              Duration(1, TimeUnit.SECONDS)
            ) {
              notifyListeners()
            }
          )
        }
      )
    }
  }

  def stop(): Unit = {
    scheduled foreach { _.cancel() }
    scheduled = None
  }

  def onTick(listener: TickListener): Unit = {
    listeners = listener :: listeners
  }

  private def notifyListeners(): Unit = {
    listeners.foreach { listener =>
      Try {
        listener (Instant.now())
      } match {
        case Success(_) =>
        case Failure(error) => println(s"Error notifying clock listener: ${error}")
      }
    }
  }
}

object Clock {
  def create(): Clock = new Clock
}
