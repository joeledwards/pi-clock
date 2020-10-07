# pi-clock

Simple clock powered by RaspberryPi & I2C driven 4x20 LCD panel

## Building

$ sbt assembly

This generates the fat jar in `target/scala-2.13/pi-clock-assembly-1.0.0.jar`

## Running

$ java -jar pi-clock-assembly-1.0.0.jar

## Configuration

You can use environment variables to configure the application:
- `PI_CLOCK_CHECK_INTEGRITY` : Exit with `0` status code (simple check for corruption)
- `PI_CLOCK_RUN_MODE` : The mode in which pi-clock should run (clock | weather-station; default is clock)
- `PI_CLOCK_I2C_ADDRESS` : The device address of the display (decimal / hex)
- `PI_CLOCK_LOG_OUTPUT` : Should the device log time update events to stdout (toggle text)
- `PI_CLOCK_DISPLAY_ENABLED` : This can be switched off to test on a non-pi system (toggle text)
- `PI_CLOCK_DISPLAY_DIMENSIONS` : The dimensions of the display (20x4 or 16x2)
- `PI_CLOCK_HUMAN_FRIENDLY` : Should the display be human friendly; standard is ISO-8601 timestamps (default: `false`)
- `PI_CLOCK_BUTTON_ENABLED` : Should the control button be enabled (default: `false`)
- `PI_CLOCK_BUTTON_PIN` : The pin associated with the control button (cycles display mode)
- `PI_CLOCK_BUTTON_NC` : Is the button normally-closed (default: `false`)
- `PI_CLOCK_INTERNET_HEALTH_CHECK` : Should the device perform regular Internet health checks (default: `false`)
- `PI_CLOCK_INTERNET_RESET_PIN` : The GPIO pin to which the reset relay is connected
- `PI_CLOCK_INTERNET_RESET_NC` : Is the reset relay normally-closed (default: `false`)
- `PI_CLOCK_INTERNET_CHECK_INTERVAL` : Frequency at which to perform Internet health checks (default: `1m`)
- `PI_CLOCK_INTERNET_OUTAGE_DURATION` : Outage duration required for an Internet connection reset (default: `5m`)
- `PI_CLOCK_INTERNET_RESET_DELAY` : Minimum delay between Internet connection resets (default: `15m`)
- `PI_CLOCK_HEALTH_SLACK_WEBHOOK` : Slack webhook for health checks.
- `PI_CLOCK_SENSE_HAT_INSTALLED` : Sense-hat system is installed (default: `false`)
- `PI_CLOCK_NOTIFICATION_SLACK_WEBHOOK` : Slack webhook for notifications.

## Notes

The control logic is ported from the C++ Arduino library [LiquidCrystal_I2C](https://github.com/marcoschwartz/LiquidCrystal_I2C.git)
