package com.buzuli.clock

import java.time.{ZoneId, ZoneOffset}
import com.buzuli.util.{Http, Strings, SysInfo}
import com.pi4j.Pi4J
import com.pi4j.context.Context
import com.typesafe.scalalogging.LazyLogging

object Main extends App with LazyLogging {
  if (Config.checkIntegrity) {
    println("Passed integrity check.")
    sys.exit(0)
  }

  // TODO: determine what (if any) additional setup is required
  implicit private val pi4jContext: Context = Pi4J.newAutoContext()

  private var displayContent: DisplayContent = Config.binary match {
    case true => DisplayBinaryTimeUtc
    case false => DisplayUtcAndHost
  }

  val addressStr = SysInfo.addresses.value.map(_.mkString("\n")).getOrElse("")
  logger.info(s"All Addresses:\n${addressStr}")

  val clock: Option[Clock] = Config.runMode match {
    case ClockMode => Some(Clock.create())
    case _ => None
  }

  val button: Option[Button] = (Config.buttonEnabled, Config.buttonPin) match {
    case (true, Some(pin)) => Some(new Button(pi4jContext, pin, Config.buttonNormallyClosed))
    case _ => None
  }
  val checkInternet : Option[InternetHealth] = Config.internetHealthCheck match {
    case true => Some(new InternetHealth(pi4jContext))
    case false => None
  }
  val display = new Display(pi4jContext, Config.displayDimensions)

  Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler {
    override def uncaughtException(thread: Thread, throwable: Throwable): Unit = {
      logger.error("Encountered un-caught exception!", throwable)
    }
  })

  sys.addShutdownHook {
    logger.info("Shutting down ...")
    clock.foreach(_.stop())
    button.foreach(_.stop())
    checkInternet.foreach(_.shutdown())
    display.shutdown()
    pi4jContext.shutdown()
    Http.shutdown()
  }

  if (Config.displayEnabled) {
    logger.info("Initializing the display ...")
    display.init()
  }

  // Configure display output on each tick of the clock
  clock.foreach { clk =>
    logger.info("Initializing the clock ...")

    clk.onTick { timestamp =>
      val lines: List[Option[String]] = display.dimensions match {
        // Build lines for 20 x 4 display
        case Display20x4 => {
          val tsLocal = timestamp.atZone(ZoneId.systemDefault)
          val tsUtc = timestamp.atZone(ZoneOffset.UTC)

          val utcTimeString = (Config.binary, Config.humanFriendly) match {
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
          val localTimeString = (Config.binary, Config.humanFriendly) match {
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
          val ipString = (Config.binary, Config.humanFriendly, SysInfo.ip.value.getOrElse("--")) match {
            case (false, true, ip) => {
              val seconds = timestamp.toString.slice(17, 19)
              val padding = " " * 20
              val ipPadded = s"${ip}${padding}"
              val separator = {
                if (checkInternet.exists(_.isNetworkDown)) {
                  "N"
                } else if (checkInternet.exists(_.isInternetDown)) {
                  "X"
                } else {
                  "|"
                }
              }
              s"${ipPadded.slice(0, 15)} ${separator} ${seconds}"
            }
            case (_, _, ip) => ip
          }

          Some(localTimeString) ::
            Some(utcTimeString) ::
            Some(host) ::
            Some(ipString) ::
            Nil
        }

        // Build lines for 16 x 2 display
        case Display16x2 => {
          val tsLocal = timestamp.atZone(ZoneId.systemDefault)
          val tsUtc = timestamp.atZone(ZoneOffset.UTC)
          val utcTimeString = s"${tsUtc.toString.slice(0, 16).replace('T', ' ')}"
          val localTimeString = s"${tsLocal.toString.slice(0, 16).replace('T', ' ')}"
          val ipString = SysInfo.ip.value.getOrElse("--")
          val host = SysInfo.host.value.getOrElse("--")

          val utcHour = Strings.padLeft('0', 5)(tsUtc.getHour.toBinaryString)
          val utcMinute = Strings.padLeft('0', 6)(tsUtc.getMinute.toBinaryString)
          val utcBinaryTime = s"${utcHour}:${utcMinute} > Z"

          val localHour = Strings.padLeft('0', 5)(tsLocal.getHour.toBinaryString)
          val localMinute = Strings.padLeft('0', 6)(tsLocal.getMinute.toBinaryString)
          val localBinaryTime = s"${localHour}:${localMinute} > L"

          displayContent match {
            case DisplayUtcAndHost => Some(utcTimeString) :: Some(host) :: Nil
            case DisplayLocalAndHost => Some(localTimeString) :: Some(host) :: Nil
            case DisplayUtcAndIp => Some(utcTimeString) :: Some(ipString) :: Nil
            case DisplayLocalAndIp => Some(localTimeString) :: Some(ipString) :: Nil
            case DisplayTimesUtcTop => Some(utcTimeString) :: Some(localTimeString) :: Nil
            case DisplayTimesLocalTop => Some(localTimeString) :: Some(utcTimeString) :: Nil
            case DisplayBinaryTimeUtc => Some(utcBinaryTime) :: Some(ipString) :: Nil
            case DisplayBinaryTimeLocal => Some(localBinaryTime) :: Some(ipString) :: Nil
          }
        }
      }

      if (Config.logDisplayUpdates) {
        logLines(lines)
      }

      if (Config.displayEnabled) {
        display.update(lines)
      }
    }
  }

  button.foreach { btn =>
    logger.info("Initializing the button ...")

    btn.onEvent {
      case ButtonPress(_, _) => {
        displayContent = displayContent.next
        logger.debug("Button pressed.")
      }
      case ButtonRelease(_, _) => {
        logger.debug("Button released.")
      }
    }
  }

  logger.info("Running ...")

  clock.foreach(_.start())
  button.foreach(_.start())
  checkInternet.foreach(_.start())

  def logLines(lines: List[Option[String]]): Unit = {
    val header = List(s"┌${"─" * display.dimensions.columns}┐")
    val footer = List(s"└${"─" * display.dimensions.columns}┘")
    val content = lines
        .map(_.getOrElse(""))
        .map(_.take(display.dimensions.columns))
        .map(_.padTo(display.dimensions.columns, ' '))
        .map(line => s"│${line}│")
    val displayText = header ::: content ::: footer

    logger.info(s"Display:\n${displayText.mkString("\n")}")
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
