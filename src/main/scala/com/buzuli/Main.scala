package com.buzuli

import java.time.ZoneId

object Main extends App {
  if (Config.checkIntegrity) {
    println("Passed integrity check.")
    sys.exit(0)
  }

  println(s"All Addresses:")
  println(SysInfo.addresses.value.map(_.mkString("\n")).getOrElse(""))

  val clock = Clock.create()
  val display = Display.create(Config.displayDimensions)

  sys.addShutdownHook {
    println("Shutting down ...")
    clock.stop()
    display.shutdown()
  }

  if (Config.displayEnabled) {
    display.init()
  }

  clock.onTick { timestamp =>
    val lines: List[Option[String]] = display.dimensions match {
      case Display20x4 => {
        val utc = s"${timestamp.toString.slice(0, 19)}Z"
        val local = s"${timestamp.atZone(ZoneId.systemDefault).toString.slice(0, 19)}L"

        Some(utc) ::
        Some(local) ::
        Some(SysInfo.host.value.getOrElse("--")) ::
        Some(SysInfo.ip.value.getOrElse("--")) ::
        Nil
      }
      case Display16x2 => {
        val utc = s"${timestamp.toString.slice(0, 16).replace('T', ' ')}"
        val local = s"${timestamp.atZone(ZoneId.systemDefault).toString.slice(0, 16).replace('T', ' ')}"

        Some(utc) ::
        Some(local) ::
        Nil
      }
    }

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
    println(s"┌${"─" * display.dimensions.columns}┐")
    lines
      .map(_.getOrElse(""))
      .map(_.take(display.dimensions.columns))
      .map(_.padTo(display.dimensions.columns, ' '))
      .foreach { line => println(s"│${line}│") }
    println(s"└${"─" * display.dimensions.columns}┘")
  }
}
