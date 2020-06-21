package com.buzuli

import java.net.{Inet6Address, InetAddress, NetworkInterface}

import scala.jdk.CollectionConverters._

object Main extends App {
  lazy val host: String = InetAddress.getLocalHost.getHostName

  lazy val addresses: List[String] = NetworkInterface
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

  lazy val ip: String = addresses
    .collectFirst { case x => x }
    .getOrElse("--")

  println(s"All Addresses:")
  println(addresses.mkString("\n"))

  val clock = new Clock
  val display = new Display

  sys.addShutdownHook {
    println("Shutting down ...")
    clock.stop()
    display.shutdown()
  }

  if (Config.displayEnabled) {
    display.init()
  }

  clock.onTick { timestamp =>
    val iso = s"${timestamp.toString().slice(0, 19)}Z"
    val lines: List[Option[String]] = Some(iso) ::
      None ::
      Some(host) ::
      Some(ip) ::
      Nil

    if (Config.logOutput) {
      logLines(lines)
    }

    if (Config.displayEnabled) {
      display.update(lines)
    }
  }

  println("Running clock ...")

  clock.start()

  def logLines(lines: List[Option[String]]): Unit = {
    println("┌────────────────────┐")
    lines
      .map(_.getOrElse(""))
      .map(_.take(20))
      .map(_.padTo(20, ' '))
      .foreach { line => println(s"│${line}│") }
    println("└────────────────────┘")
  }
}
