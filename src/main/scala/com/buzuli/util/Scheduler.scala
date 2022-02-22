package com.buzuli.util

import java.time.Instant
import java.util.concurrent.{ScheduledFuture, ScheduledThreadPoolExecutor, TimeUnit}

import scala.concurrent.duration.Duration

trait Scheduled {
  def cancel(interrupt: Boolean = false): Unit
}

object Scheduled {
  def of[T](future: ScheduledFuture[T]): Scheduled = (interrupt: Boolean) => future.cancel(interrupt)
}

class Scheduler {
  val executor = new ScheduledThreadPoolExecutor(2)

  def runnable(task: => Unit): Runnable = () => task

  def runAfter(delay: Duration)(task: => Unit): Scheduled = {
    Scheduled.of(executor.schedule(runnable(task), delay.toNanos, TimeUnit.NANOSECONDS))
  }

  def runAt(ts: Instant)(task: => Unit): Scheduled = {
    val now = Instant.now()
    val delay = Duration(Math.max(0, ts.toEpochMilli - now.toEpochMilli), TimeUnit.MILLISECONDS)
    runAfter(delay)(task)
  }

  def runEvery(
    delay: Duration,
    startImmediately: Boolean = false,
    fixedInterval: Boolean = true
  )(task: => Unit): Scheduled = Scheduled.of(
    fixedInterval match {
      case true => executor.scheduleAtFixedRate(
        runnable(task),
        if (startImmediately) 0 else delay.toNanos,
        delay.toNanos,
        TimeUnit.NANOSECONDS
      )
      case false => executor.scheduleWithFixedDelay(
        runnable(task),
        if (startImmediately) 0 else delay.toNanos,
        delay.toNanos,
        TimeUnit.NANOSECONDS
      )
    }
  )
}

object Scheduler {
  private lazy val defaultScheduler: Scheduler = new Scheduler
  def default: Scheduler = defaultScheduler
  def create(): Scheduler = new Scheduler
}