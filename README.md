# pi-clock

Simple clock powered by RaspberryPi & I2C driven 4x20 LCD panel

## Building

$ sbt assembly

This generates the fat jar in `target/scala-2.13/pi-clock-assembly-1.0.0.jar`

## Running

$ java -jar pi-clock-assembly-1.0.0.jar

## Configuration

You can use environment variables to configure the application:
- `PI_CLOCK_I2C_ADDRESS` : The device address of the display (decimal / hex)
- `PI_CLOCK_DISPLAY_ENABLED` : This can be switched off to test on a non-pi system (toggle text)
- `PI_CLOCK_LOG_OUTPUT` : Should the device log time update events to stdout (toggle text)
- `PI_CLOCK_CHECK_INTEGRITY` : Exit with `0` status code (simple check for corruption)

## Notes

The control logic is ported from the C++ Arduino library [LiquidCrystal_I2C](https://github.com/marcoschwartz/LiquidCrystal_I2C.git)
