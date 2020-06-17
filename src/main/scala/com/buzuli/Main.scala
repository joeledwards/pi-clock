package com.buzuli

object Main extends App {
  val clock = new Clock
  val display = new Display

  sys.addShutdownHook {
    println("Shutting down ...")
    clock.stop()
    display.shutdown()
  }

  if (Config.displayEnabled) {
    display.init()
  }

  clock.onTick { timestamp =>
    val lines = Some(timestamp.toString) ::
      Nil

    if (Config.logOutput) {
      logLines(lines)
    }

    if (Config.displayEnabled) {
      display.update(lines)
    }
  }

  println("Running clock ...")

  clock.start()

  def logLines(lines: List[Option[String]]): Unit = {
    lines.map(_.getOrElse("")).foreach(println(_))
  }
}
