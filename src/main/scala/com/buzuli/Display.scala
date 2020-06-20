package com.buzuli

import com.pi4j.wiringpi.I2C
import scala.util.{Success, Try}

class Display {
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

  private var fd: Option[Int] = None

  private val backlight: Int = LCD_BACKLIGHT
  private val displayFunction: Int = LCD_4BITMODE | LCD_2LINE | LCD_5x8DOTS
  private var displayControl: Int = LCD_DISPLAYON | LCD_CURSOROFF | LCD_BLINKOFF
  private var displayMode: Int = LCD_ENTRYLEFT | LCD_ENTRYSHIFTDECREMENT

  def init(): Unit = {
    fd = Try {
      I2C.wiringPiI2CSetup(Config.i2cAddress)
    } match {
      case Success(fd) if fd != -1 => Some(fd)
      case _ => None
    }

    fd match {
      case None => {
        println(s"Setup failed for address ${Config.i2cAddress}")
      }
      case Some(_) => {
        println(s"Device ${Config.i2cAddress} accessible at fd ${fd}")

        delay(50)

        write(backlight)
        delay(1000)

        write4Bits(0x03 << 4)
        delay(5)
        write4Bits(0x03 << 4)
        delay(5)
        write4Bits(0x03 << 4)
        delay(1)
        write4Bits(0x02 << 4)

        function()
        control()
        clear()
        mode()
        home()

        delay(200)
      }
    }
  }

  def shutdown(): Unit = fd foreach { fd =>
    clear()
    delay(100)
    displayOff()
    delay(100)
  }

  def update(text: List[Option[String]]): Unit = {
    setCursor(0, 0)
    printIIC('A')
    // HOW DO I WRITE A CHARACTER?

    // TODO: update the display
    // - track the prior display content
    // - diff with the old with the new display content
    // - only write the differences
  }

  // Helper functions
  def delay(millis: Long): Unit = Thread.sleep(millis)

  def write(data: Int): Unit = fd foreach {
    I2C.wiringPiI2CWrite(_, data | backlight)
  }

  def pulseEnable(data: Int): Unit = {
    // Ignoring the required delays since the JVM should be plenty slow...
    write(data | ENABLE)
    // delay >450ns
    write(data & ~ENABLE)
    // delay >450ns
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
    delay(2)
  }

  def home(): Unit = {
    command(LCD_RETURNHOME)
    delay(2)
  }

  def setCursor(column: Int, row: Int): Unit = {
    val offset = column + row match {
      case 0 => 0x00
      case 1 => 0x40
      case 2 => 0x14
      case 3 => 0x54
    }
    command(LCD_SETDDRAMADDR | (column + offset))
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
