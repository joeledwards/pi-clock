#! /bin/bash

export PI_CLOCK_CHECK_INTEGRITY=no  #-----------# Exit with `0` status code (simple check for corruption; default: `false`)
export PI_CLOCK_I2C_BUS_FOR_DISPLAY=0x01 #------# The I2C bus of the display (decimal / hex; default: `0`)
export PI_CLOCK_I2C_DEVICE_FOR_DISPLAY=0x27 #---# The I2C device address of the display (decimal / hex; default `0`)
export PI_CLOCK_LOG_DISPLAY_UPDATES=off #-------# Should the device log detailed display updates to stdout (toggle text; default: `off`)
export PI_CLOCK_LOG_TIMING_INFO=off #-----------# Should the device log details on how long delays and updates take (toggle text; default: `off`)
export PI_CLOCK_DISPLAY_ENABLED=on  #-----------# This can be switched off to test on a non-pi system (toggle text; default: `off`)
export PI_CLOCK_DISPLAY_DIMENSIONS=20x4 #-------# The dimensions of the display (`20x4` or `16x2`)
export PI_CLOCK_HUMAN_FRIENDLY=yes #------------# Should the display be human friendly; standard is ISO-8601 timestamps (default: `false`)
export PI_CLOCK_BUTTON_ENABLED=no  #------------# Should the control button be enabled (default: `false`)
export PI_CLOCK_BUTTON_PIN="" #-----------------# The pin associated with the control button (cycles display mode)
export PI_CLOCK_BUTTON_NC="" #------------------# Is the button normally-closed (default: `false`)
export PI_CLOCK_INTERNET_HEALTH_CHECK=off #-----# Should the device perform regular Internet health checks (default: `false`)
export PI_CLOCK_INTERNET_POWER_PIN="" #---------# The GPIO pin to which the power relay is connected
export PI_CLOCK_INTERNET_POWER_HIGH="" #--------# Power is on when the power pin is pulled high (default: `false`)
export PI_CLOCK_INTERNET_CHECK_INTERVAL="" #----# Frequency at which to perform Internet health checks (default: `1m`)
export PI_CLOCK_INTERNET_CHECK_TIMEOUT="" #-----# Health check HTTP request timeout (default: `5s`)
export PI_CLOCK_INTERNET_OUTAGE_DURATION="" #---# Outage duration required for an Internet connection reset (default: `5m`)
export PI_CLOCK_INTERNET_RESET_DELAY="" #-------# Minimum delay between Internet connection resets (default: `15m`)
export PI_CLOCK_HEALTH_SLACK_WEBHOOK="" #-------# Slack webhook for health checks.
export PI_CLOCK_SENSE_HAT_INSTALLED=no  #-------# Sense-hat system is installed (default: `false`)
export PI_CLOCK_NOTIFICATION_SLACK_WEBHOOK="" #-# Slack webhook for notifications.
export PI_CLOCK_CUSTOM_DISPLAY_FILE_PATH="display.txt" #-# Custom display content

jar_path="./pi-clock-2.0.0.jar"

java -jar $jar_path
