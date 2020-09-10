package com.buzuli.util

import java.time.Instant
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration

object Time {
  def now(): Instant = Instant.now
  def since(whence: Instant): Duration = diff(now, whence)
  def diff(start: Instant, end: Instant): Duration = {
    Duration(
      java.time.Duration.between(start, end).toMillis,
      TimeUnit.MILLISECONDS
    )
  }
}
