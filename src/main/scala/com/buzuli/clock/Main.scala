package com.buzuli.clock

import java.time.ZoneId
import java.util.concurrent.TimeUnit

import com.buzuli.util.{Http, SysInfo}

import scala.concurrent.duration.Duration

object Main extends App {
  if (Config.checkIntegrity) {
    println("Passed integrity check.")
    sys.exit(0)
  }

  private var displayContent: DisplayContent = DisplayUtcAndIp

  println(s"All Addresses:")
  println(SysInfo.addresses.value.map(_.mkString("\n")).getOrElse(""))

  val clock: Option[Clock] = Config.runMode match {
    case ClockMode => Some(Clock.create())
    case _ => None
  }
  val button: Option[Button] = (Config.buttonEnabled, Config.buttonPin) match {
    case (true, Some(pin)) => Some(Button.create(pin, Config.buttonNormallyClosed))
    case _ => None
  }
  val checkInternet : Option[InternetHealth] = Config.internetHealthCheck match {
    case true => Some(new InternetHealth)
    case false => None
  }
  val display = Display.create(Config.displayDimensions)

  Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler {
    override def uncaughtException(thread: Thread, throwable: Throwable): Unit = {
      println("Encountered un-caught exception!")
      throwable.printStackTrace()
    }
  })

  sys.addShutdownHook {
    println("Shutting down ...")
    clock.foreach(_.stop())
    button.foreach(_.stop())
    checkInternet.foreach(_.shutdown())
    display.shutdown()
    Http.shutdown()
  }

  if (Config.displayEnabled) {
    println("Initializing the display ...")
    display.init()
  }

  clock.foreach { clk =>
    println("Initializing the clock ...")

    clk.onTick { timestamp =>
      val lines: List[Option[String]] = display.dimensions match {
        case Display20x4 => {
          val utc = Config.humanFriendly match {
            case true => s"${timestamp.toString.slice(0, 16).replace('T', ' ')} < Z"
            case false => s"${timestamp.toString.slice(0, 19)}Z"
          }
          val localTs = timestamp.atZone(ZoneId.systemDefault)
          val local = Config.humanFriendly match {
            case true => s"${localTs.toString.slice(0, 16).replace('T', ' ')} < L"
            case false => s"${localTs.toString.slice(0, 19)}L"
          }

          val ipStr = (Config.humanFriendly, SysInfo.ip.value.getOrElse("--")) match {
            case (true, ip) => {
              val seconds = timestamp.toString.slice(17, 19)
              val padding = " " * 20
              val ipPadded = s"${ip}${padding}"
              s"${ipPadded.slice(0, 15)} | ${seconds}"
            }
            case (false, ip) => ip
          }

          Some(local) ::
            Some(utc) ::
            Some(SysInfo.host.value.getOrElse("--")) ::
            Some(ipStr) ::
            Nil
        }
        case Display16x2 => {
          val utc = s"${timestamp.toString.slice(0, 16).replace('T', ' ')}"
          val local = s"${timestamp.atZone(ZoneId.systemDefault).toString.slice(0, 16).replace('T', ' ')}"
          val ip = SysInfo.ip.value.getOrElse("--")

          displayContent match {
            case DisplayUtcAndIp => Some(utc) :: Some(ip) :: Nil
            case DisplayLocalAndIp => Some(local) :: Some(ip) :: Nil
            case DisplayTimesUtcTop => Some(utc) :: Some(local) :: Nil
            case DisplayTimesLocalTop => Some(local) :: Some(utc) :: Nil
          }
        }
      }

      if (Config.logOutput) {
        logLines(lines)
      }

      if (Config.displayEnabled) {
        display.update(lines)
      }
    }
  }

  button.map { btn =>
    println("Initializing the button ...")

    btn.onEvent { _ match {
      case ButtonPress(_, _) => {
        displayContent = displayContent.next
        println("Button pressed.")
      }
      case ButtonRelease(_, _) => {
        println("Button released.")
      }
    } }
  }

  println("Running ...")

  clock.foreach(_.start())
  button.foreach(_.start())
  checkInternet.foreach(_.start())

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

sealed trait DisplayContent {
  def next: DisplayContent
}

case object DisplayUtcAndIp extends DisplayContent {
  override def next: DisplayContent = DisplayLocalAndIp
}
case object DisplayLocalAndIp extends DisplayContent {
  override def next: DisplayContent = DisplayTimesUtcTop
}
case object DisplayTimesUtcTop extends DisplayContent {
  override def next: DisplayContent = DisplayTimesLocalTop
}
case object DisplayTimesLocalTop extends DisplayContent {
  override def next: DisplayContent = DisplayUtcAndIp
}
