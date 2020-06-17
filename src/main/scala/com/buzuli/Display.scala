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

  def init(): Unit = {
    fd = Try {
      I2C.wiringPiI2CSetup(Config.i2cAddress)
    } match {
      case Success(fd) if fd != -1 => {
        println(s"Device ${Config.i2cAddress} accessible at fd ${fd}")

        I2C.wiringPiI2CWrite(fd, 0x03)
        I2C.wiringPiI2CWrite(fd, 0x03)
        I2C.wiringPiI2CWrite(fd, 0x03)
        I2C.wiringPiI2CWrite(fd, 0x02)

        I2C.wiringPiI2CWrite(fd, LCD_FUNCTIONSET | LCD_2LINE | LCD_5x8DOTS | LCD_4BITMODE)
        I2C.wiringPiI2CWrite(fd, LCD_DISPLAYCONTROL | LCD_DISPLAYON)
        //I2C.wiringPiI2CWrite(fd, LCD_DISPLAYCONTROL | LCD_BACKLIGHT)
        I2C.wiringPiI2CWrite(fd, LCD_CLEARDISPLAY)
        I2C.wiringPiI2CWrite(fd, LCD_ENTRYMODESET | LCD_ENTRYLEFT)

        Thread.sleep(200)

        Some(fd)
      }
      case _ => {
        println(s"Setup failed for address ${Config.i2cAddress}")
        None
      }
    }
  }

  def shutdown(): Unit = fd foreach { fd =>
    I2C.wiringPiI2CWrite(fd, LCD_CLEARDISPLAY)
    I2C.wiringPiI2CWrite(fd, LCD_DISPLAYOFF)
  }

  def update(text: List[Option[String]]): Unit = {
    fd foreach { fd =>
      writeChar(0, 0, 'A') // A test

      // TODO: update the display
      // - track the prior display content
      // - diff with the old with the new display content
      // - only write the differences
    }
  }

  def writeChar(row: Int, column: Int, character: Char): Unit = {
    val offset = column + row match {
      case 0 => 0
      case 1 => 0x40
      case 2 => 0x14
      case 3 => 0x54
    }
    lcdPosition(offset)
    lcdWrite(character, REGISTER_SELECT)
  }

  def lcdPosition(offset: Int): Unit = fd foreach {
    I2C.wiringPiI2CWrite(_, LCD_DISPLAYMOVE | offset)
  }

  def lcdStrobe(data: Int): Unit = fd foreach { fd =>
    I2C.wiringPiI2CWrite(fd, data | ENABLE | LCD_BACKLIGHT)
    Thread.sleep(1)
    I2C.wiringPiI2CWrite(fd, (data & ~ENABLE) | LCD_BACKLIGHT)
    Thread.sleep(1)
  }

  def lcdWriteFourBits(data: Int): Unit = fd foreach { fd =>
    I2C.wiringPiI2CWrite(fd, data | LCD_BACKLIGHT)
    lcdStrobe(data)
  }

  def lcdWrite(cmd: Int, mode: Int = 0): Unit = {
    lcdWriteFourBits(mode | (cmd & 0xF0))
    lcdWriteFourBits(mode | ((cmd << 4) & 0xF0))
  }

  def lcdWriteChar(character: Int, mode: Int = 1): Unit = {
    lcdWriteFourBits(mode | (character & 0xF0))
    lcdWriteFourBits(mode | ((character << 4) & 0xF0))
  }

  def lcdClear(): Unit = {
    lcdWrite(LCD_CLEARDISPLAY)
    lcdWrite(LCD_RETURNHOME)
  }
}
