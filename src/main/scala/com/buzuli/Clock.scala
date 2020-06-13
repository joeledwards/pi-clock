package com.buzuli

import java.time.Instant

class Clock {
  type TickListener = Instant => Unit

  private var started: Boolean = false
  private var listeners: List[TickListener]

  def start(): Unit = {
    if (!started) {
      // TODO: schedule regular ticks

      started = true
    }
  }

  def onTick(listener: TickListener): Unit = {
    listeners = listener :: listeners
  }
}
