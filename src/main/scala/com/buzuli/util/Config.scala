package com.buzuli.util

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

trait ConfigSupplier {
  def get(key: String): Option[String]

  def getAs[T](key: String)(convert: String => Option[T]): Option[T] = {
    Try {
      get(key) flatMap (convert(_))
    } match {
      case Success(v) => v
      case Failure(e) => None
    }
  }

  def parseToggle(value: String): Option[Boolean] = {
    value.toLowerCase match {
      case "true" => Some(true)
      case "false" => Some(false)
      case "yes" => Some(true)
      case "no" => Some(false)
      case "on" => Some(true)
      case "off" => Some(false)
      case "1" => Some(true)
      case "0" => Some(false)
      case _ => None
    }
  }

  def parseDuration(durationString: String): Option[Duration] = {
    Option(durationString) match {
      case None => None
      case Some(text) =>
        val (valueText, timeUnit) = text match {
          case t if t.endsWith("ns") => (t.slice(0, t.size - 2), TimeUnit.NANOSECONDS)
          case t if t.endsWith("us") => (t.slice(0, t.size - 2), TimeUnit.MICROSECONDS)
          case t if t.endsWith("ms") => (t.slice(0, t.size - 2), TimeUnit.MILLISECONDS)
          case t if t.endsWith("s") => (t.slice(0, t.size - 1), TimeUnit.SECONDS)
          case t if t.endsWith("m") => (t.slice(0, t.size - 1), TimeUnit.MINUTES)
          case t if t.endsWith("h") => (t.slice(0, t.size - 1), TimeUnit.HOURS)
          case t if t.endsWith("d") => (t.slice(0, t.size - 1), TimeUnit.DAYS)
          case t => (t, TimeUnit.SECONDS)
        }

        try {
          Some(Duration(valueText.toLong, timeUnit))
        } catch {
          case e: NumberFormatException => None
        }
    }
  }

  def getInt(key: String): Option[Int] = getAs(key) { _ match {
    case v if v.startsWith("0x") => Some(Integer.parseInt(v.slice(2, v.length), 16))
    case v => Some(Integer.parseInt(v))
  } }

  def getToggle(key: String): Option[Boolean] = getAs(key)(parseToggle(_))

  def getDuration(key: String): Option[Duration] = getAs(key)(parseDuration(_))
}

object ConfigSupplier {
  private class MapConfigSupplier(map: Map[String, String] = Map.empty) extends ConfigSupplier {
    override def get(key: String): Option[String] = map.get(key)
  }

  def of(map: Map[String, String]): ConfigSupplier = new MapConfigSupplier(map)
}

object Env extends ConfigSupplier {
  def get(key: String): Option[String] = Try {
    val value = Option(System.getenv(key))
    println(s"Env: ${key} => ${value}")
    value
  } match {
    case Failure(_) => None
    case Success(entry) => entry map { _.trim } filter { !_.isEmpty }
  }
}
