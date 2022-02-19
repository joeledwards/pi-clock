package com.buzuli.clock

import com.pi4j.io.gpio._
import com.pi4j.io.gpio.event.{PinEvent, PinEventType, PinListener}

import scala.util.{Failure, Success, Try}

/**
 *
 *
 * @param pin the pin
 * @param normallyClosed
 */
class Button(
  val pin: Int,
  val normallyClosed: Boolean
) {
  type ButtonEventHandler = ButtonEvent => Unit

  private var listeners: List[ButtonEventListener] = Nil
  private var gpio: Option[GpioProvider] = None

  def onPress(handler: ButtonEventHandler): Unit = addListener(ButtonPressListener(handler))
  def onRelease(handler: ButtonEventHandler): Unit = addListener(ButtonReleaseListener(handler))
  def onEvent(handler: ButtonEventHandler): Unit = addListener(ButtonActionListener(handler))

  /* Listen only once */
  def onePress(handler: ButtonEventHandler): Unit = addListener(ButtonPressListener(handler, once = true))
  def oneRelease(handler: ButtonEventHandler): Unit = addListener(ButtonReleaseListener(handler, once = true))
  def oneEvent(handler: ButtonEventHandler): Unit = addListener(ButtonActionListener(handler, once = true))

  private def addListener(listener: ButtonEventListener): Unit = {
    listeners = listener :: listeners
  }

  private def handleWith(handler: ButtonEventHandler)(event: ButtonEvent): Unit = {
    Try {
      handler(event)
    } match {
      case Success(_) =>
      case Failure(error) => println(s"Error handling button event.\nEvent: ${event}\nError: ${error}")
    }
  }

  private def pressed(): Unit = {
    val event = ButtonPress(pin, !normallyClosed)
    listeners = listeners filter { _ match {
      case ButtonActionListener(handler, once) => {
        handleWith(handler)(event)
        !once
      }
      case ButtonPressListener(handler, once) => {
        handleWith(handler)(event)
        !once
      }
      case _ => true
    } }
  }

  private def released(): Unit = {
    val event = ButtonRelease(pin, normallyClosed)
    listeners = listeners filter { _ match {
      case ButtonActionListener(handler, once) => {
        handleWith(handler)(event)
        !once
      }
      case ButtonReleaseListener(handler, once) => {
        handleWith(handler)(event)
        !once
      }
      case _ => true
    } }
  }

  def start(): Unit = {
    gpio match {
      case Some(_) =>
      case None => {
        gpio = Some(new RaspiGpioProvider(RaspiPinNumberingScheme.BROADCOM_PIN_NUMBERING))
        val gpioPin: Pin = RaspiPin.getPinByAddress(pin)

        gpio.foreach { g =>
          g.`export`(gpioPin, PinMode.DIGITAL_INPUT)
          g.addListener(gpioPin, new PinListener {
            override def handlePinEvent(event: PinEvent): Unit = {
              if (pin != event.getPin.getAddress)
                return
              if (event.getEventType != PinEventType.DIGITAL_STATE_CHANGE)
                return
              (normallyClosed, gpio.map(_.getState(event.getPin))) match {
                // Normally Closed
                case (true, Some(PinState.LOW)) => pressed()
                case (true, Some(PinState.HIGH)) => released()
                // Normally Open
                case (false, Some(PinState.HIGH)) => pressed()
                case (false, Some(PinState.LOW)) => released()
                // No pin configured
                case (_, None) =>
              }
            }
          })
        }
      }
    }
  }

  def stop(): Unit = {
    gpio foreach { _.removeAllListeners() }
    gpio = None
  }
}

object Button {
  def create(pin: Int, normallyClosed: Boolean): Button = new Button(pin, normallyClosed)
}

trait ButtonEventListener {
  def handler: ButtonEvent => Unit
  def once: Boolean
}
case class ButtonActionListener(handler: ButtonEvent => Unit, once: Boolean = false) extends ButtonEventListener
case class ButtonPressListener(handler: ButtonEvent => Unit, once: Boolean = false) extends ButtonEventListener
case class ButtonReleaseListener(handler: ButtonEvent => Unit, once: Boolean = false) extends ButtonEventListener

trait ButtonEvent {
  def pin: Int
  def closed: Boolean
}
case class ButtonPress(pin: Int, closed: Boolean) extends ButtonEvent
case class ButtonRelease(pin: Int, closed: Boolean) extends ButtonEvent
