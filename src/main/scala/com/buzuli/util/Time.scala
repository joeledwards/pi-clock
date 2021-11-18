package com.buzuli.util

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, DurationLong}
import scala.util.Try

object Time {
  val NANOS_PER_MICRO: Long = 1000L
  val NANOS_PER_MILLI: Long = NANOS_PER_MICRO * 1000L
  val NANOS_PER_SECOND: Long = NANOS_PER_MILLI * 1000L
  val NANOS_PER_MINUTE: Long = NANOS_PER_SECOND * 60L
  val NANOS_PER_HOUR: Long = NANOS_PER_MINUTE * 60L
  val NANOS_PER_DAY: Long = NANOS_PER_HOUR * 24L

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

  private val FloatMatcher = "([0-9]+(?:.[0-9]+)?)".r
  def parseDuration(durationString: String): Option[Duration] = {
    durationString
      .split(" ")
      .map(_.trim)
      .filter(_.nonEmpty)
      .collect {
        case s"${FloatMatcher(value)}" => Try(Duration(value.toDouble, TimeUnit.SECONDS)).toOption
        case s"${FloatMatcher(value)}d" => Try(Duration(value.toDouble, TimeUnit.DAYS)).toOption
        case s"${FloatMatcher(value)}h" => Try(Duration(value.toDouble, TimeUnit.HOURS)).toOption
        case s"${FloatMatcher(value)}m" => Try(Duration(value.toDouble, TimeUnit.MINUTES)).toOption
        case s"${FloatMatcher(value)}s" => Try(Duration(value.toDouble, TimeUnit.SECONDS)).toOption
        case s"${FloatMatcher(value)}ms" => Try(Duration(value.toDouble, TimeUnit.MILLISECONDS)).toOption
        case s"${FloatMatcher(value)}us" => Try(Duration(value.toDouble, TimeUnit.MICROSECONDS)).toOption
        case s"${FloatMatcher(value)}ns" => Try(Duration(value.toDouble, TimeUnit.NANOSECONDS)).toOption
        case _ => None
      }
      .foldLeft[Option[Duration]](None) { (acc, next) =>
        acc match {
          case None => next
          case Some(prev) => next.map(_ + prev)
        }
      }
  }
}
