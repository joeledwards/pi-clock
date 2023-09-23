package com.buzuli.util

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

object Timing {
  case class Sampling(
    count: Int,
    elapsed: Duration,
    min: Duration,
    max: Duration,
    avgNs: Double,
  ) {
    override def toString: String = {
      def pd(d: Duration): String = Time.prettyDuration(d)

      s"count=${count} elapsed=${pd(elapsed)} min=${pd(min)} max=${pd(max)} avgNs=${avgNs}"
    }
  }

  def sample[T](activity: => T): (T, Duration) = {
    val start = System.nanoTime
    val r = activity
    val end = System.nanoTime
    val elapsed = Duration(end - start, TimeUnit.NANOSECONDS)

    (r, elapsed)
  }

  def samples(count: Int)(activity: => Unit): Sampling = {
    var min = Long.MaxValue
    var max = Long.MinValue
    var elapsed = 0L
    var start = System.nanoTime
    var remaining = count

    while (remaining > 0) {
      remaining -= 1
      activity
      val end = System.nanoTime
      val delay = end - start
      elapsed += delay
      min = if (delay < min) delay else min
      max = if (delay > max) delay else max
      start = end
    }

    val avgNs = elapsed.toDouble / count.toDouble

    Sampling(
      count,
      Duration(elapsed, TimeUnit.NANOSECONDS),
      Duration(min, TimeUnit.NANOSECONDS),
      Duration(max, TimeUnit.NANOSECONDS),
      avgNs
    )
  }

  def delaySync(duration: Duration): Unit = {
    val start = System.nanoTime
    var remaining = duration.toNanos

    while (remaining > 0) {
      remaining match {
        case sd if sd >= 500000 => Thread.sleep(sd / 1000000L, (sd % 1000000L).toInt)
        case nd if nd > 10000 => Thread.sleep(0)
        case _ => System.nanoTime
      }

      remaining = duration.toNanos - (System.nanoTime - start)
    }
  }
}
