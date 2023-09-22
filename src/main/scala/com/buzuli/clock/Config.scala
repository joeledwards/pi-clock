package com.buzuli.clock

import com.buzuli.util.Env

import scala.concurrent.duration._

object Config {
  lazy val checkIntegrity: Boolean = Env.getToggle("PI_CLOCK_CHECK_INTEGRITY").getOrElse(false)

  lazy val buttonPin: Option[Int] = Env.getInt("PI_CLOCK_BUTTON_PIN")
  lazy val buttonEnabled: Boolean = Env.getToggle("PI_CLOCK_BUTTON_ENABLED").getOrElse(false)
  lazy val buttonNormallyClosed: Boolean = Env.getToggle("PI_CLOCK_BUTTON_NC").getOrElse(false)

  lazy val internetHealthCheck: Boolean = Env.getToggle("PI_CLOCK_INTERNET_HEALTH_CHECK").getOrElse(false)
  lazy val internetPowerPin: Option[Int] = Env.getInt("PI_CLOCK_INTERNET_POWER_PIN")
  lazy val internetPowerHigh: Boolean = Env.getToggle("PI_CLOCK_INTERNET_POWER_HIGH").getOrElse(false)
  lazy val internetCheckTimeout: Duration = Env.getDuration("PI_CLOCK_INTERNET_CHECK_TIMEOUT").getOrElse(5.seconds)
  lazy val internetCheckInterval: Duration = Env.getDuration("PI_CLOCK_INTERNET_CHECK_INTERVAL").getOrElse(1.minute)
  lazy val internetOutageDuration: Duration = Env.getDuration("PI_CLOCK_INTERNET_OUTAGE_DURATION").getOrElse(5.minutes)
  lazy val internetResetDelay: Duration = Env.getDuration("PI_CLOCK_INTERNET_RESET_DELAY").getOrElse(15.minutes)

  lazy val notificationSlackWebhook: Option[String] = Env.get("PI_CLOCK_NOTIFICATION_SLACK_WEBHOOK")

  lazy val senseHatInstalled: Boolean = Env.getToggle("PI_CLOCK_SENSE_HAT_INSTALLED").getOrElse(false)

  lazy val humanFriendly: Boolean = Env.getToggle("PI_CLOCK_HUMAN_FRIENDLY").getOrElse(false)
  lazy val binary: Boolean = Env.getToggle("PI_CLOCK_BINARY").getOrElse(false)

  lazy val i2cBusForDisplay: Int = Env.getInt("PI_CLOCK_I2C_BUS_FOR_DISPLAY").getOrElse(0)
  lazy val i2cDeviceForDisplay: Int = Env.getInt("PI_CLOCK_I2C_DEVICE_FOR_DISPLAY").getOrElse(0)
  lazy val logDisplayUpdates: Boolean = Env.getToggle("PI_CLOCK_LOG_DISPLAY_UPDATES").getOrElse(false)
  lazy val logTimingInfo: Boolean = Env.getToggle("PI_CLOCK_LOG_TIMING_INFO").getOrElse(false)
  lazy val displayEnabled: Boolean = Env.getToggle("PI_CLOCK_DISPLAY_ENABLED").getOrElse(false)
  lazy val displayDimensions: DisplayDimensions = {
    Env.getAs("PI_CLOCK_DISPLAY_DIMENSIONS") {
      DisplayDimensions.of
    } getOrElse {
      Display20x4
    }
  }
  lazy val customDisplayFilePath: Option[String] = Env.get("PI_CLOCK_CUSTOM_DISPLAY_FILE_PATH")
}