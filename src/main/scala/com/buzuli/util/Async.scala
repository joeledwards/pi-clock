package com.buzuli.util

import java.time.Instant
import java.util.concurrent.TimeUnit

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.Duration

object Async {
  def delay(millis: Long): Future[Duration] = delay(Duration(millis, TimeUnit.MILLISECONDS))
  def delay(duration: Duration): Future[Duration] = {
    val start = Instant.now
    val p: Promise[Duration] = Promise()
    Scheduler.default.runAfter(duration) {
      val end = Instant.now
      val duration = Duration(
        java.time.Duration.between(start, end).toMillis,
        TimeUnit.MILLISECONDS
      )
      p.success(duration)
    }
    p.future
  }
}
