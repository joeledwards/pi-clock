package com.buzuli

import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant}

class Clock {
  type TickListener = (Instant) => Unit

  private var scheduled: Option[Scheduled] = None
  private var listeners: List[TickListener] = Nil
  private val scheduler = new Scheduler

  def start(): Unit = {
    if (!scheduled.isDefined) {
      scheduled = Some(scheduler.runAt(Instant.now.plusSeconds(1).truncatedTo(ChronoUnit.SECONDS)) {
        scheduled = Some(scheduler.runEvery(Duration.ofSeconds(1)) {
          notifyListeners()
        })
      })
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
    listeners.foreach { _(Instant.now()) }
  }
}

object Clock {
  def create(): Clock = new Clock
}
