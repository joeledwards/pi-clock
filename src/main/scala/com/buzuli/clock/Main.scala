package com.buzuli.clock

import java.nio.file.{Files, Paths}
import java.time.Instant
import com.buzuli.util.{Http, Koozie, Strings, SysInfo, Time, Timing, Types}
import com.pi4j.Pi4J
import com.pi4j.context.Context
import com.typesafe.scalalogging.LazyLogging

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.io.Source
import scala.language.postfixOps
import scala.util.Try

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
  
  pi4jContext.value.foreach {
    logContextInfo(_)
  }

  if (Config.logTimingInfo) {
    testDelays()
  }

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

  val customDisplayLines: Option[Koozie[List[Option[String]]]] = customDisplayLineSource

  // Configure display output on each tick of the clock
  clock.foreach { clk =>
    logger.info("Initializing the clock ...")

    clk.onTick { timestamp =>
      customDisplayLines.foreach(l => DisplayContent.setCustomLines(l.fresh))

      val lines: List[Option[String]] = DisplayContent.getDisplayLines(timestamp, checkInternet, displayContent)

      if (Config.logDisplayUpdates) {
        logLines(lines)
      }

      if (Config.displayEnabled) {
        val (_ , duration) = Timing.sample {
          display.foreach(_.update(lines))
        }

        if (Config.logTimingInfo) {
          logger.info(s"Display update took ${Time.prettyDuration(duration)}")
        }
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

  def testDelays(): Unit = { 
    logger.info("Testing delays ...")

    val tnt = Timing.samples(1000) { System.nanoTime }
    logger.info(s"System.nanoTime => ${tnt}")

    val tms = Timing.samples(1000) { System.currentTimeMillis }
    logger.info(s"System.currentTimeMillis => ${tms}")

    val tdz = Timing.samples(1000) { Timing.delaySync(Duration.Zero) }
    logger.info(s"delaySync(0) => ${tdz}")

    val tdu = Timing.samples(1000) { Timing.delaySync(Duration(10, TimeUnit.MICROSECONDS)) }
    logger.info(s"delaySync(10us) => ${tdu}")

    val tdm = Timing.samples(1000) { Timing.delaySync(Duration(1, TimeUnit.MILLISECONDS)) }
    logger.info(s"delaySync(1ms) => ${tdm}")

    List(
      Duration(1, TimeUnit.NANOSECONDS),
      Duration(100, TimeUnit.NANOSECONDS),

      Duration(5, TimeUnit.MICROSECONDS),
      Duration(200, TimeUnit.MICROSECONDS),
      Duration(499, TimeUnit.MICROSECONDS),
      Duration(500, TimeUnit.MICROSECONDS),
      Duration(999, TimeUnit.MICROSECONDS),

      Duration(1, TimeUnit.MILLISECONDS),
      Duration(20, TimeUnit.MILLISECONDS),
    ) foreach { delay =>
      val (_, actual) = Timing.sample {
        Timing.delaySync(delay)
      }
      logger.info(s"Timing.delaySync: expected=${Time.prettyDuration(delay)} actual=${Time.prettyDuration(actual)}")
    }
  }

  var lastLoadedFileModificationTime: Option[Instant] = None
  def customDisplayLineSource: Option[Koozie[List[Option[String]]]] = {
      Config.customDisplayFilePath map { p =>
        val path = Paths.get(p)
        Koozie.sync(
          {
            val (lines, lastLoadTime) = Types.option.unzip(
              Try(
                Files.getLastModifiedTime(path)
              ).toOption.filter(mTime =>
                !lastLoadedFileModificationTime.exists(_.isAfter(mTime.toInstant))
              ).flatMap({ mTime =>
                Try {
                  val fileContent = Source
                    .fromFile(path.toFile)
                    .getLines
                    .toList
                    .map(l => if (l.nonEmpty) Some(l) else None)

                  (fileContent, mTime.toInstant)
                } toOption
              })
            )

            lastLoadedFileModificationTime = lastLoadTime

            lines
          },
          Some(Duration(1, TimeUnit.SECONDS))
        )
      }
    }

  def logContextInfo(context: Context): Unit = {
    logger.debug(s"""Pi4J 2 Platforms:
${Strings.stream(context.platforms.describe.print(_))}
    """)

    logger.debug(s"""Pi4J 2 Providers:
${Strings.stream(context.providers.describe.print(_))}
    """)

    logger.debug(s"""Pi4J 2 Registry:
${Strings.stream(context.registry.describe.print(_))}
    """)
  }
}
