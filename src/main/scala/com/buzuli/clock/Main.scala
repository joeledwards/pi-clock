package com.buzuli.clock

import java.time.{ZoneId, ZoneOffset}
import com.buzuli.util.{Http, Strings, SysInfo, Time}

import java.time.temporal.{ChronoField, TemporalField}

object Main extends App {
  if (Config.checkIntegrity) {
    println("Passed integrity check.")
    sys.exit(0)
  }

  //private var displayContent: DisplayContent = DisplayUtcAndHost
  private var displayContent: DisplayContent = DisplayBinaryTimeUtc

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
          val tsLocal = timestamp.atZone(ZoneId.systemDefault)
          val tsUtc = timestamp.atZone(ZoneOffset.UTC)

          val utc = (Config.binary, Config.humanFriendly) match {
            case (true, _) => {
              val utcHour = Strings.padLeft('0', 5)(tsUtc.getHour.toBinaryString)
              val utcMinute = Strings.padLeft('0', 6)(tsUtc.getMinute.toBinaryString)
              val utcSecond = Strings.padLeft('0', 6)(tsUtc.getSecond.toBinaryString)
              s"${utcHour}:${utcMinute}.${utcSecond}Z"
            }
            case (_, true) => s"${timestamp.toString.slice(0, 16).replace('T', ' ')} < Z"
            case _ => s"${timestamp.toString.slice(0, 19)}Z"
          }
          val localTs = timestamp.atZone(ZoneId.systemDefault)
          val local = (Config.binary, Config.humanFriendly) match {
            case (true, _) => {
              val localHour = Strings.padLeft('0', 5)(tsLocal.getHour.toBinaryString)
              val localMinute = Strings.padLeft('0', 6)(tsLocal.getMinute.toBinaryString)
              val localSecond = Strings.padLeft('0', 6)(tsLocal.getSecond.toBinaryString)
              s"${localHour}:${localMinute}.${localSecond}L"
            }
            case (_, true) => s"${localTs.toString.slice(0, 16).replace('T', ' ')} < L"
            case _ => s"${localTs.toString.slice(0, 19)}L"
          }

          val host = SysInfo.host.value.getOrElse("--")
          val ipStr = (Config.binary, Config.humanFriendly, SysInfo.ip.value.getOrElse("--")) match {
            case (false, true, ip) => {
              val seconds = timestamp.toString.slice(17, 19)
              val padding = " " * 20
              val ipPadded = s"${ip}${padding}"
              s"${ipPadded.slice(0, 15)} | ${seconds}"
            }
            case (_, _, ip) => ip
          }

          Some(local) ::
            Some(utc) ::
            Some(host) ::
            Some(ipStr) ::
            Nil
        }
        case Display16x2 => {
          val tsLocal = timestamp.atZone(ZoneId.systemDefault)
          val tsUtc = timestamp.atZone(ZoneOffset.UTC)
          val utc = s"${tsUtc.toString.slice(0, 16).replace('T', ' ')}"
          val local = s"${tsLocal.toString.slice(0, 16).replace('T', ' ')}"
          val ip = SysInfo.ip.value.getOrElse("--")
          val host = SysInfo.host.value.getOrElse("--")

          val utcHour = Strings.padLeft('0', 5)(tsUtc.getHour.toBinaryString)
          val utcMinute = Strings.padLeft('0', 6)(tsUtc.getMinute.toBinaryString)
          val utcBinaryTime = s"${utcHour}:${utcMinute} > Z"

          val localHour = Strings.padLeft('0', 5)(tsLocal.getHour.toBinaryString)
          val localMinute = Strings.padLeft('0', 6)(tsLocal.getMinute.toBinaryString)
          val localBinaryTime = s"${localHour}:${localMinute} > L"

          displayContent match {
            case DisplayUtcAndHost => Some(utc) :: Some(host) :: Nil
            case DisplayLocalAndHost => Some(local) :: Some(host) :: Nil
            case DisplayUtcAndIp => Some(utc) :: Some(ip) :: Nil
            case DisplayLocalAndIp => Some(local) :: Some(ip) :: Nil
            case DisplayTimesUtcTop => Some(utc) :: Some(local) :: Nil
            case DisplayTimesLocalTop => Some(local) :: Some(utc) :: Nil
            case DisplayBinaryTimeUtc => Some(utcBinaryTime) :: Some(ip) :: Nil
            case DisplayBinaryTimeLocal => Some(localBinaryTime) :: Some(ip) :: Nil
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

  button.foreach { btn =>
    println("Initializing the button ...")

    btn.onEvent {
      case ButtonPress(_, _) => {
        displayContent = displayContent.next
        println("Button pressed.")
      }
      case ButtonRelease(_, _) => {
        println("Button released.")
      }
    }
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

case object DisplayUtcAndHost extends DisplayContent {
  override def next: DisplayContent = DisplayLocalAndHost
}
case object DisplayLocalAndHost extends DisplayContent {
  override def next: DisplayContent = DisplayUtcAndIp
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
  override def next: DisplayContent = DisplayBinaryTimeUtc
}
case object DisplayBinaryTimeUtc extends DisplayContent {
  override def next: DisplayContent = DisplayBinaryTimeLocal
}
case object DisplayBinaryTimeLocal extends DisplayContent {
  override def next: DisplayContent = DisplayUtcAndHost
}
