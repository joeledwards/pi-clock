package com.buzuli

import java.time.{Duration, Instant}
import java.util.concurrent.{ScheduledFuture, ScheduledThreadPoolExecutor, TimeUnit}

trait Scheduled {
  def cancel(interrupt: Boolean = false): Unit
}

object Scheduled {
  def of[T](future: ScheduledFuture[T]): Scheduled = new Scheduled {
    override def cancel(interrupt: Boolean = false): Unit = future.cancel(interrupt)
  }
}

class Scheduler {
  val executor = new ScheduledThreadPoolExecutor(1)

  def runnable(task: => Unit): Runnable = new Runnable {
    def run(): Unit = task
  }

  def runAfter(delay: Duration)(task: => Unit): Scheduled = {
    Scheduled.of(executor.schedule(runnable(task), delay.toNanos, TimeUnit.NANOSECONDS))
  }

  def runAt(ts: Instant)(task: => Unit): Scheduled = {
    val now = Instant.now()
    val delay = Duration.ofMillis(Math.max(0, ts.toEpochMilli - now.toEpochMilli))
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
