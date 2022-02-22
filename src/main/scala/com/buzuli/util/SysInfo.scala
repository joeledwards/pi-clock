package com.buzuli.util

import java.net.{Inet6Address, InetAddress, NetworkInterface}

import scala.concurrent.duration.Duration
import scala.io.Source
import scala.util.{Failure, Success, Try}
import scala.jdk.CollectionConverters._

object SysInfo {
  val host: Koozie[String] = Koozie.sync(
    Some(InetAddress.getLocalHost.getHostName),
    Some(Duration(1, "minute"))
  )

  val addresses: Koozie[List[String]] = Koozie.sync(
    {
      Try {
        NetworkInterface
          .getNetworkInterfaces
          .asScala
          .toList
          .filter(!_.isVirtual)
          .filter(!_.isLoopback)
          .filter(!_.getName.toLowerCase.contains("docker"))
          .filter(!_.getName.toLowerCase.startsWith("tun"))
          .filter(_.isUp)
          .flatMap(_.getInetAddresses.asScala.toList)
          .filter(!_.isAnyLocalAddress)
          .filter(!_.isLoopbackAddress)
          .filter(!_.isMulticastAddress)
          .filter(!_.isInstanceOf[Inet6Address])
          .map(_.getHostAddress)
          .filter(!_.startsWith("127"))
      } match {
        case Success(addrs) => Some(addrs)
        case Failure(error) => {
          println(s"Error fetching IP address(es): ${error.getMessage}")
          //error.printStackTrace()
          None
        }
      }
    },
    Some(Duration(1, "minute"))
  )

  val ip: Koozie[String] = Koozie.sync(
    addresses.value.flatMap(_.collectFirst { case x => x }),
    Some(Duration(1, "minute"))
  )

  def readFile(path: String): Option[List[String]] = Try {
    var lines: List[String] = Nil
    val source = Source.fromFile(path)
    source.getLines().collect { line =>
      lines = lines :+ line
    }
    source.close()
    lines
  } match {
    case Success(lines) => Some(lines)
    case Failure(error) => {
      println(s"Error reading file ${path}: ${error.getMessage}")
      //error.printStackTrace()
      None
    }
  }
}
