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
    val iso = s"${timestamp.toString().slice(0, 19)}Z"
    val lines: List[Option[String]] = Some(iso) :: None :: None :: None :: Nil

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
