package com.buzuli.clock

import java.time.{ZoneId, ZoneOffset}
import com.buzuli.util.{Http, Koozie, Strings, SysInfo, Timing}
import com.pi4j.Pi4J
import com.pi4j.context.Context
import com.typesafe.scalalogging.LazyLogging

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

object Main extends App with LazyLogging {
  if (Config.checkIntegrity) {
    println("Passed integrity check.")
    sys.exit(0)
  }

  val addressStr = SysInfo.addresses.value.map(_.mkString("\n")).getOrElse("")
  logger.info(s"All Addresses:\n${addressStr}")

  val clock: Option[Clock] = Some(Clock.create())

  private val pi4jContext: Koozie[Context] = Koozie.sync(
    { Some(Pi4J.newAutoContext()) },
    None,
    true
  )

  pi4jContext.value.foreach { context =>
    println("= Platforms =======================")
    context.platforms.describe.print(System.out)
    println("===================================")
    println("")
  }

  pi4jContext.value.foreach { context =>
    println("= Providers =======================")
    context.providers.describe.print(System.out)
    println("===================================")
    println("")
  }

  pi4jContext.value.foreach { context =>
    println("= Registry ========================")
    context.registry.describe.print(System.out)
    println("===================================")
    println("")
  }

  logger.info("Testing delays ...")

  val tnt = Timing.samples(1000) { System.nanoTime }
  val tms = Timing.samples(1000) { System.currentTimeMillis }
  val td0 = Timing.samples(1000) { Timing.delaySync(Duration.Zero) }
  val td1 = Timing.samples(1000) { Timing.delaySync(Duration(1, TimeUnit.MILLISECONDS)) }

  logger.info(s"System.nanoTime => ${tnt}")
  logger.info(s"System.currentTimeMillis => ${tms}")
  logger.info(s"delaySync(0) => ${td0}")
  logger.info(s"delaySync(1ms) => ${td1}")

  val button: Option[Button] = (Config.buttonEnabled, Config.buttonPin) match {
    case (true, Some(pin)) => pi4jContext.value.map(new Button(_, pin, Config.buttonNormallyClosed))
    case _ => None
  }
  val checkInternet : Option[InternetHealth] = Config.internetHealthCheck match {
    case true => pi4jContext.value.map(new InternetHealth(_))
    case false => None
  }
  val display: Option[I2CDisplay] = Config.displayEnabled match {
    case true => pi4jContext.value.map(new I2CDisplay(_, Config.displayDimensions))
    case _ => None
  }

  Thread.setDefaultUncaughtExceptionHandler((thread: Thread, throwable: Throwable) => {
    logger.error(s"Encountered un-caught exception in thread ${thread.getName}!", throwable)
  })

  sys.addShutdownHook {
    logger.info("Shutting down ...")
    clock.foreach(_.stop())
    button.foreach(_.stop())
    checkInternet.foreach(_.shutdown())
    display.foreach(_.shutdown())
    pi4jContext.stale.foreach(_.shutdown())
    Http.shutdown()
  }

  if (Config.displayEnabled) {
    logger.info("Initializing the display ...")
    display.foreach(_.init())
  }

  private var displayContent: DisplayContent = Config.binary match {
    case true => DisplayBinaryTimeUtc
    case false => DisplayUtcAndHost
  }

  // Configure display output on each tick of the clock
  clock.foreach { clk =>
    logger.info("Initializing the clock ...")

    clk.onTick { timestamp =>
      val lines: List[Option[String]] = DisplayContent.getDisplayLines(timestamp, checkInternet, displayContent)

      if (Config.logDisplayUpdates) {
        logLines(lines)
      }

      if (Config.displayEnabled) {
        display.foreach(_.update(lines))
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

  logger.info("Starting services ...")

  clock.foreach(_.start())
  button.foreach(_.start())
  checkInternet.foreach(_.start())

  logger.info("Running ...")

  def logLines(lines: List[Option[String]]): Unit = {
    val header = List(s"┌${"─" * Config.displayDimensions.columns}┐")
    val footer = List(s"└${"─" * Config.displayDimensions.columns}┘")
    val content = lines
        .map(_.getOrElse(""))
        .map(_.take(Config.displayDimensions.columns))
        .map(_.padTo(Config.displayDimensions.columns, ' '))
        .map(line => s"│${line}│")
    val displayText = header ::: content ::: footer

    logger.info(s"Display:\n${displayText.mkString("\n")}")
  }
}
