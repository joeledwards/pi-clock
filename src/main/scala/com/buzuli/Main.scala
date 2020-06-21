package com.buzuli

import java.net.{Inet6Address, InetAddress, NetworkInterface}

import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._

object Main extends App {
  if (Config.checkIntegrity) {
    println("Passed integrity check.")
    sys.exit(0)
  }

  val host: Koozie[String] = Koozie.sync(
    Some(InetAddress.getLocalHost.getHostName),
    Some(Duration(1, "minute"))
  )

  val addresses: Koozie[List[String]] = Koozie.sync(
    Some(NetworkInterface
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
    ),
    Some(Duration(1, "minute"))
  )

  val ip: Koozie[String] = Koozie.sync(
    addresses.value.flatMap(_.collectFirst { case x => x }),
    Some(Duration(1, "minute"))
  )

  println(s"All Addresses:")
  println(addresses.value.map(_.mkString("\n")).getOrElse(""))

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
      Some(host.value.getOrElse("--")) ::
      Some(ip.value.getOrElse("--")) ::
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
