package com.buzuli

import java.timer.TimerTask
import java.util.Timer

class Scheduler {
  type Task = () => Unit

  def runAfter(delay: Duration)(task: Task): Scheduled = {

  }

  def repeatAtInterval(
    interval: Duration,
    stopOnError: Boolean = false,
    runImmediately: Boolean = false
  )

  def repeatWithDelay(
    interval: Duration,
    stopOnError: Boolean = false,
    runImmediately: Boolean = false
  )

  def runEvery(
  )(task: Task): Schedule = {
    val scheduled = new Schedule(interval, stopOnError)

    if (runImmediately) {

    }
  }

  class Schedule(
    val interval: Interval
  ) {
    private var active: Boolean = false

    protected def 
    protected def scheduleNext

    def inactive: Boolean = !active

    def cancel(): Boolean = {
      if (active) {
        active = false
      }
    }
  }
}

