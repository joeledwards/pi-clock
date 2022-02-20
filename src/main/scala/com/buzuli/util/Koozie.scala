package com.buzuli.util

import java.time.Instant

import scala.concurrent.duration.Duration

/**
 * Koozie is a single-value cache which may be refreshed at an interval.
 * A synchronous implementation will refresh when the resource
 * is requested. An asynchronous version will refresh in the background.
 */
trait Koozie[T] {
  def value: Option[T]
}

class SyncKoozie[T](
  refresher: => Option[T],
  interval: Option[Duration],
  eager: Boolean = false
) extends Koozie[T] {
  private var lastRefresh: Option[Instant] = None
  private var _value: Option[T] = if (eager) refresh() else None
  override def value: Option[T] = maybeRefresh { _value }
  private def now: Instant = Instant.now

  private def refresh(): Option[T] = {
    lastRefresh = Some(now)
    refresher
  }

  private def maybeRefresh(post: => Option[T]): Option[T] = {
    _value = _value match {
      case None => refresh()
      case _ => interval match {
        case None => _value
        case Some(delay) => lastRefresh match {
          case None => refresh()
          case Some(last) if (now.toEpochMilli - last.toEpochMilli) >= delay.toMillis => refresh()
          case _ => _value
        }
      }
    }
    post
  }
}

object Koozie {
  def sync[T](
     refresher: => Option[T],
     interval: Option[Duration],
     eager: Boolean = false
   ): Koozie[T] = new SyncKoozie[T](refresher, interval, eager)

  // TODO: create AsyncKoozie
  /*
  def async[T](
    refresher: => Option[T],
    interval: Option[Duration],
    eager: Boolean = false
  ): Koozie[T] = new AsyncKoozie[T](refresher, interval, eager)
  */
}
