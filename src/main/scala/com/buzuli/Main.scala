package com.buzuli

object Main extends App {
  val clock = new Clock
  val display = new Display

  clock.onTick { timestamp =>
    display.update(timestamp)
  }

  clock.start()
}
