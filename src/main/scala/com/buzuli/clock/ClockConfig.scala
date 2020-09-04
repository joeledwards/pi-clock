package com.buzuli.clock

import scala.util.{Failure, Success, Try}
import com.buzuli.util.Env

sealed trait RunMode
case object ClockMode extends RunMode
case object WeatherStationMode extends RunMode

object RunMode {
  def of(mode: String): Option[RunMode] = mode match {
    case "weather-station" => Some(WeatherStationMode)
    case _ => Some(ClockMode)
  }
}

object Config {
  lazy val checkIntegrity: Boolean = Env.getToggle("PI_CLOCK_CHECK_INTEGRITY").getOrElse(false)
  lazy val i2cAddress: Int = Env.getInt("PI_CLOCK_I2C_ADDRESS").getOrElse(0)
  lazy val logOutput: Boolean = Env.getToggle("PI_CLOCK_LOG_OUTPUT").getOrElse(false)

  lazy val buttonPin: Option[Int] = Env.getInt("PI_CLOCK_BUTTON_PIN")
  lazy val buttonEnabled: Boolean = Env.getToggle("PI_CLOCK_BUTTON_ENABLED").getOrElse(false)
  lazy val buttonNormallyClosed: Boolean = Env.getToggle("PI_CLOCK_BUTTON_NC").getOrElse(false)

  lazy val internetHealthCheck: Boolean = Env.getToggle("PI_CLOCK_INTERNET_HEALTH_CHECK").getOrElse(false)
  lazy val internetResetPin: Option[Int] = Env.getInt("PI_CLOCK_INTERNET_RESET_PIN")
  lazy val internetResetNc: Boolean = Env.getToggle("PI_CLOCK_INTERNET_RESET_NC").getOrElse(false)

  lazy val notificationSlackWebhook: Option[String] = Env.get("PI_CLOCK_NOTIFICATION_SLACK_WEBHOOK")

  lazy val senseHatInstalled: Boolean = Env.getToggle("PI_CLOCK_SENSE_HAT_INSTALLED").getOrElse(false)

  lazy val humanFriendly: Boolean = Env.getToggle("PI_CLOCK_HUMAN_FRIENDLY").getOrElse(false)

  lazy val displayEnabled: Boolean = Env.getToggle("PI_CLOCK_DISPLAY_ENABLED").getOrElse(false)
  lazy val displayDimensions: DisplayDimensions = Env.getAs("PI_CLOCK_DISPLAY_DIMENSIONS") {
    DisplayDimensions.of(_)
  } getOrElse {
    Display20x4
  }
  lazy val runMode: RunMode = Env.getAs("PI_CLOCK_RUN_MODE") {
    RunMode.of(_)
  } getOrElse {
    ClockMode
  }
}