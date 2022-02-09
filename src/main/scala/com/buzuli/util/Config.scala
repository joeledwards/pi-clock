package com.buzuli.util

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

trait ConfigSupplier {
  def get(key: String): Option[String]

  def getAs[T](key: String)(convert: String => Option[T]): Option[T] = {
    get(key) flatMap (convert(_))
  }

  def getListAs[T](key: String)(convert: String => Option[T]): Option[List[T]] = {
    getStrings(key) map { list =>
      list.flatMap(convert)
    }
  }

  def parseToggle(value: String): Option[Boolean] = {
    value.toLowerCase match {
      case "enabled" => Some(true)
      case "disabled" => Some(false)
      case "enable" => Some(true)
      case "disable" => Some(false)
      case "true" => Some(true)
      case "false" => Some(false)
      case "yes" => Some(true)
      case "no" => Some(false)
      case "on" => Some(true)
      case "off" => Some(false)
      case "t" => Some(true)
      case "f" => Some(false)
      case "y" => Some(true)
      case "n" => Some(false)
      case "1" => Some(true)
      case "0" => Some(false)
      case _ => None
    }
  }

  def getStrings(key: String): Option[List[String]] = getAs(key) { text =>
    text.trim match {
      case "" => None
      case t => {
        Some(t
          .split(",")
          .toList
          .map(_.trim)
          .filter(_.nonEmpty) // Only keep non-empty strings in the list
        ).filter(_.nonEmpty) // Ensure we have at least one value in the list
      }
    }
  }

  def getInt(key: String): Option[Int] = getAs(key) {
    case v if v.startsWith("0x") => Try(Integer.parseInt(v.slice(2, v.length), 16)).toOption
    case v => Try(Integer.parseInt(v)).toOption
  }

  def getToggle(key: String): Option[Boolean] = getAs(key)(parseToggle)

  def getDuration(key: String): Option[Duration] = getAs(key)(Time.parseDuration)
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
