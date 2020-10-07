package com.buzuli.util

import java.time.Instant
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration

object Time {
  val NANOS_PER_MICRO = 1000L
  val NANOS_PER_MILLI = NANOS_PER_MICRO * 1000L
  val NANOS_PER_SECOND = NANOS_PER_MILLI * 1000L
  val NANOS_PER_MINUTE = NANOS_PER_SECOND * 60L
  val NANOS_PER_HOUR = NANOS_PER_MINUTE * 60L
  val NANOS_PER_DAY = NANOS_PER_HOUR * 24L

  def now(): Instant = Instant.now
  def since(whence: Instant): Duration = diff(whence, now)
  def diff(start: Instant, end: Instant): Duration = {
    Duration(
      java.time.Duration.between(start, end).toMillis,
      TimeUnit.MILLISECONDS
    )
  }
  def thousandths(value: Number): String = s"${(value.intValue() % 1000) + 1000}".slice(1, 4)
  def prettyDuration(duration: Duration): String = duration.toNanos match {
    case nanos if nanos >= NANOS_PER_DAY  => s"${nanos / NANOS_PER_DAY}d ${nanos / NANOS_PER_HOUR % 24}h"
    case nanos if nanos >= NANOS_PER_HOUR => s"${nanos / NANOS_PER_HOUR}h ${nanos / NANOS_PER_MINUTE % 60}m"
    case nanos if nanos >= NANOS_PER_MINUTE => s"${nanos / NANOS_PER_MINUTE}m ${nanos / NANOS_PER_SECOND % 60}s"
    case nanos if nanos >= NANOS_PER_SECOND => s"${nanos / NANOS_PER_SECOND}.${thousandths(nanos / NANOS_PER_MILLI % 1000)}s"
    case nanos if nanos >= NANOS_PER_MILLI => s"${nanos / NANOS_PER_MILLI}.${thousandths(nanos / NANOS_PER_MICRO % 1000)}ms"
    case nanos => s"${nanos / NANOS_PER_MICRO}.${thousandths(nanos % 1000)}us"
  }
}
