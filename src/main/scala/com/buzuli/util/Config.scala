package com.buzuli.util

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

  def getInt(key: String): Option[Int] = getAs(key) { _ match {
    case v if v.startsWith("0x") => Some(Integer.parseInt(v.slice(2, v.length), 16))
    case v => Some(Integer.parseInt(v))
  } }
  def getToggle(key: String): Option[Boolean] = getAs(key)(parseToggle(_))
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
