package com.buzuli.clock

import com.pi4j.context.Context
import com.pi4j.io.gpio._
import com.pi4j.io.gpio.digital.{DigitalInput, DigitalState, DigitalStateChangeListener}
import com.typesafe.scalalogging.LazyLogging

import scala.util.{Failure, Success, Try}

/**
 * @param pin the pin on which to listen for button presses
 * @param normallyClosed indicates whether
 */
class Button(
  val pi4jContext: Context,
  val pin: Int,
  val normallyClosed: Boolean
) extends LazyLogging {
  type ButtonEventHandler = ButtonEvent => Unit

  private var listeners: List[ButtonEventListener] = Nil
  private var buttonPin: Option[DigitalInput] = None
  private var eventListener: Option[DigitalStateChangeListener] = None

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
      logger.debug(s"Button event => ${event}")
      handler(event)
    } match {
      case Success(_) =>
      case Failure(error) => logger.error(s"Error handling button event.\nEvent: ${event}\nError: ${error}", error)
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

  def start(): Unit = this.synchronized {
    buttonPin match {
      case Some(_) =>
      case None => {
        buttonPin = Try {
          val config = DigitalInput
            .newConfigBuilder(pi4jContext)
            .name("Display Button Pin")
            .id("display-button-pin")
            .address(pin)
            .build

          val input = pi4jContext.digitalInput().create(config)
          logger.info(s"Adding listener for pin ${pin}")

          eventListener = Some({
            val listener: DigitalStateChangeListener = { pinEvent =>
              (normallyClosed, pinEvent.state) match {
                // Normally Closed
                case (true, DigitalState.LOW) => pressed()
                case (true, DigitalState.HIGH) => released()
                // Normally Open
                case (false, DigitalState.HIGH) => pressed()
                case (false, DigitalState.LOW) => released()
              }
            }

            input.addListener(listener)

            listener
          })

          input
        } match {
          case Success(di) => {
            logger.info(s"GPIO initialization completed for button on pin ${pin}.")
            Some(di)
          }
          case Failure(error) => {
            logger.error("Error initializing GPIO for button", error)
            None
          }
        }
      }
    }
  }

  def stop(): Unit = this.synchronized {
    buttonPin.zip(eventListener) foreach {
      case (pin, listener) => pin.removeListener(listener)
    }

    buttonPin = None
  }
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
