package com.buzuli.clock

import com.buzuli.util.Timing
import com.pi4j.context.Context
import com.pi4j.io.i2c.{I2C, I2CProvider}
import com.typesafe.scalalogging.LazyLogging

import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.language.postfixOps

import scala.concurrent.duration._

class I2CDisplay(pi4j: Context, val dimensions: DisplayDimensions) extends LazyLogging {
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  // commands
  val LCD_CLEARDISPLAY = 0x01
  val LCD_RETURNHOME = 0x02
  val LCD_ENTRYMODESET = 0x04
  val LCD_DISPLAYCONTROL = 0x08
  val LCD_CURSORSHIFT = 0x10
  val LCD_FUNCTIONSET = 0x20
  val LCD_SETCGRAMADDR = 0x40
  val LCD_SETDDRAMADDR = 0x80

  // flags for display entry mode
  val LCD_ENTRYRIGHT = 0x00
  val LCD_ENTRYLEFT = 0x02
  val LCD_ENTRYSHIFTINCREMENT = 0x01
  val LCD_ENTRYSHIFTDECREMENT = 0x00

  // flags for display on/off control
  val LCD_DISPLAYON = 0x04
  val LCD_DISPLAYOFF = 0x00
  val LCD_CURSORON = 0x02
  val LCD_CURSOROFF = 0x00
  val LCD_BLINKON = 0x01
  val LCD_BLINKOFF = 0x00

  // flags for display/cursor shift
  val LCD_DISPLAYMOVE = 0x08
  val LCD_CURSORMOVE = 0x00
  val LCD_MOVERIGHT = 0x04
  val LCD_MOVELEFT = 0x00

  // flags for function set
  val LCD_8BITMODE = 0x10
  val LCD_4BITMODE = 0x00
  val LCD_2LINE = 0x08
  val LCD_1LINE = 0x00
  val LCD_5x10DOTS = 0x04
  val LCD_5x8DOTS = 0x00

  // flags for backlight control
  val LCD_BACKLIGHT = 0x08
  val LCD_NOBACKLIGHT = 0x00

  val ENABLE = 0x04
  val READ_WRITE = 0x02
  val REGISTER_SELECT = 0x01

  private var i2c: Option[I2C] = None

  private def generateDisplay(): Array[Array[Option[Char]]] = {
    Array.fill[Array[Option[Char]]](dimensions.rows) {
      Array.fill[Option[Char]](dimensions.columns)(None)
    }
  }
  private var shadowDisplay = generateDisplay()

  private var backlight: Int = LCD_BACKLIGHT
  private val displayFunction: Int = LCD_4BITMODE | LCD_2LINE | LCD_5x8DOTS
  private var displayControl: Int = LCD_DISPLAYON | LCD_CURSOROFF | LCD_BLINKOFF
  private var displayMode: Int = LCD_ENTRYLEFT | LCD_ENTRYSHIFTDECREMENT

  def init(): Unit = {
    i2c = Try {
      // The pigpio-i2c provider was broken last time I tried to use it.
      // The linuxfs-i2c works well, and is fast enough display updates.
      val i2cProvider = "linuxfs-i2c"

      logger.info("Creating I2C Config ...")
      val i2cConfig = I2C.newConfigBuilder(pi4j)
        .name("display")
        .id("display")
        .bus(Config.i2cBusForDisplay)
        .device(Config.i2cDeviceForDisplay)
        .provider(i2cProvider)
        .build

      logger.info("Creating I2C Object ...")
      pi4j.create(i2cConfig)
    } match {
      case Failure(error) => {
        error.printStackTrace()
        logger.warn(s"I2C setup failed for bus=${Config.i2cBusForDisplay} device=${Config.i2cDeviceForDisplay}")
        None
      }
      case Success(display) => {
        logger.info(s"Display is available on I2C")
        Some(display)
      }
    }

    i2c foreach { _ =>
      logger.info("Initializing the I2C display ...")
      
      delay(50.millis) // > 40ms

      logger.info("Enabling the backlight")
      write(backlight)
      delay(1.second)

      logger.info("Setting 4-bit mode")
      write4Bits(0x03 << 4)
      delay(4200.micros) // >4100us
      write4Bits(0x03 << 4)
      delay(4200.micros) // >4100ms
      write4Bits(0x03 << 4)
      delay(160.micros) // >150us
      write4Bits(0x02 << 4)

      logger.info("LCD Function Set")
      function()

      logger.info("Display Controls (display on, cursor)")
      control()

      logger.info("Clear Display")
      clear()

      logger.info("Entry Mode (left, shift decrement)")
      mode()

      logger.info("Return Home")
      home()

      // Enforce a long enough wait here before allowing any external updates
      delay(200.millis)

      logger.info("I2C display initialization complete.")
    }
  }

  def shutdown(): Unit = i2c foreach { _ =>
    backlight = LCD_NOBACKLIGHT
    setCursor(0,0)
    printIIC(' ')
    clear()
    delay(100.millis)
    write(LCD_NOBACKLIGHT)
    delay(1.second)
    displayOff()
    delay(100.millis)

    i2c = None
  }

  case class LcdUpdate(row: Int, col: Int, char: Char)

  def computeUpdates(lines: List[Option[String]]): List[LcdUpdate] = {
    var updates: List[LcdUpdate] = Nil
    val newDisplay = generateDisplay()

    lines
      .take(dimensions.rows)
      .zipWithIndex
      .map(x => x._1.map((_, x._2)))
      .filter(_.isDefined)
      .map(_.get)
      .foreach { case (line, row) =>
        line
          .take(dimensions.columns)
          .zipWithIndex
          .foreach { case (char, col) =>
            newDisplay(row)(col) = Some(char)
          }
      }

    newDisplay.zipWithIndex.foreach { case (r, row) =>
      r.zipWithIndex.foreach { case (cell, col) =>
        if (cell != shadowDisplay(row)(col)) {
          updates = LcdUpdate(row, col, cell.getOrElse(' ')) :: updates
        }
      }
    }

    shadowDisplay = newDisplay

    updates
  }

  var updating = false

  def update(lines: List[Option[String]]): Unit = synchronized {
    if (updating) {
      logger.warn("Update in progress. Skipping.")
    } else {
      updating = true

      Try {
        computeUpdates(lines) foreach { case LcdUpdate(row, col, char) =>
          if (Config.logDisplayUpdates) {
            logger.debug(s"Updating character: (${row}, ${col}) => '${char}'")
          }
          setCursor(row, col)
          printIIC(char)
        }
      } match {
        case Failure(error) => logger.error("Error updating clock", error)
        case Success(_) =>
      }

      updating = false
    }
  }

  def delay(duration: Duration): Unit = Timing.delaySync(duration)

  def write(data: Int): Unit = i2c foreach { i =>
    i.write(data | backlight)
  }

  def pulseEnable(data: Int): Unit = {
    write(data | ENABLE)
    // delay >450ns (the JVM should be plenty slow)
    delay(500.nanos)

    write(data & ~ENABLE)
    // delay >37us (do we need to sleep here?)
    delay(40.micros)
  }

  def write4Bits(data: Int): Unit = {
    write(data)
    pulseEnable(data)
  }

  def command(cmd: Int): Unit = send(cmd, 0)

  def registerChar(location: Int, charmap: Array[Int]): Unit = {
    val location = 0x7
    command(LCD_SETCGRAMADDR | (location << 3))

    // TODO: write the custom char
  }

  def send(value: Int, mode: Int): Unit = {
    val highNib = value & 0xf0
    val lowNib = (value << 4) & 0xf0
    write4Bits(highNib | mode)
    write4Bits(lowNib | mode)
  }

  def printIIC(value: Int): Unit = send(value, REGISTER_SELECT)

  def clear(): Unit = {
    command(LCD_CLEARDISPLAY)
    delay(2.millis)
  }

  def home(): Unit = {
    command(LCD_RETURNHOME)
    delay(2.millis)
  }

  def setCursor(row: Int, column: Int): Unit = {
    val rowOffset = row match {
      case 0 => 0x00
      case 1 => 0x40
      case 2 => 0x14
      case 3 => 0x54
    }
    command(LCD_SETDDRAMADDR | (column + rowOffset))
  }

  // Display function
  def function(): Unit = command(LCD_FUNCTIONSET | displayFunction)

  // Display control
  def control(): Unit = command(LCD_DISPLAYCONTROL | displayControl)
  def control(pre: => Unit): Unit = { pre; control() }

  def blinkOn(): Unit = control { displayControl |= LCD_BLINKON }
  def blinkOff(): Unit = control { displayControl &= ~LCD_BLINKOFF }

  def cursorOn(): Unit = control { displayControl |= LCD_CURSORON }
  def cursorOff(): Unit = control { displayControl &= ~LCD_CURSOROFF }

  def displayOn(): Unit = control { displayControl |= LCD_DISPLAYON }
  def displayOff(): Unit = control { displayControl &= ~LCD_DISPLAYOFF }

  // Display mode
  def mode(): Unit = command(LCD_ENTRYMODESET | displayMode)
  def mode(pre: => Unit): Unit = { pre; mode() }

  def setLeftToRight(): Unit = mode { displayMode |= LCD_ENTRYLEFT }
  def setRightToLeft(): Unit = mode { displayMode &= ~LCD_ENTRYLEFT }

  def autoScrollOn(): Unit = mode { displayMode |= LCD_ENTRYSHIFTINCREMENT }
  def autoScrollOff(): Unit = mode { displayMode &= ~LCD_ENTRYSHIFTINCREMENT }

  // Dynamic controls
  def scrollLeft(): Unit = command(LCD_CURSORSHIFT | LCD_DISPLAYMOVE | LCD_MOVELEFT)
  def scrollRight(): Unit = command(LCD_CURSORSHIFT | LCD_DISPLAYMOVE | LCD_MOVERIGHT)
}

sealed abstract class DisplayDimensions(val rows: Int, val columns: Int)
case object Display20x4 extends DisplayDimensions(4, 20)
case object Display16x2 extends DisplayDimensions(2, 16)

object DisplayDimensions {
  def of(dimensions: String): Option[DisplayDimensions] = dimensions match {
    case "20x4" => Some(Display20x4)
    case "4x20" => Some(Display20x4)
    case "16x2" => Some(Display16x2)
    case "2x16" => Some(Display16x2)
    case _ => None
  }
}
