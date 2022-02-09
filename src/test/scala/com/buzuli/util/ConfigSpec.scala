package com.buzuli.util

import com.buzuli.UnitSpec

import scala.concurrent.duration._


class ConfigSpec extends UnitSpec {
  "ConfigSupplier" when {
    "fetching string values" should {
      "return Some when found" in {
        assert(ConfigSupplier.of(Map("name" -> "")).get("name").contains(""))
        assert(ConfigSupplier.of(Map("name" -> "buzuli")).get("name").contains("buzuli"))
      }
      "return None when not found" in {
        assert(ConfigSupplier.of(Map("nay" -> "buzuli")).get("name").isEmpty)
      }
    }
    "fetching string lists" should {
      "return Some when at least one non-empty part is found" in {
        assert(ConfigSupplier.of(Map("name" -> "buzuli")).getStrings("name").contains(List("buzuli")))
        assert(ConfigSupplier.of(Map("name" -> ",buzuli")).getStrings("name").contains(List("buzuli")))
        assert(ConfigSupplier.of(Map("name" -> "buzuli,")).getStrings("name").contains(List("buzuli")))
        assert(ConfigSupplier.of(Map("name" -> ",buzuli")).getStrings("name").contains(List("buzuli")))
        assert(ConfigSupplier.of(Map("name" -> " , buzuli , ")).getStrings("name").contains(List("buzuli")))
        assert(ConfigSupplier.of(Map("name" -> " a ,b , c")).getStrings("name").contains(List("a", "b", "c")))
      }
      "return None when there wasn't there were no non-empty elements" in {
        assert(ConfigSupplier.of(Map("nay" -> "buzuli")).getStrings("name").isEmpty)
        assert(ConfigSupplier.of(Map("name" -> "")).getStrings("name").isEmpty)
        assert(ConfigSupplier.of(Map("name" -> " ")).getStrings("name").isEmpty)
        assert(ConfigSupplier.of(Map("name" -> " ,")).getStrings("name").isEmpty)
        assert(ConfigSupplier.of(Map("name" -> ", ")).getStrings("name").isEmpty)
        assert(ConfigSupplier.of(Map("name" -> " , ")).getStrings("name").isEmpty)
        assert(ConfigSupplier.of(Map("name" -> ",")).getStrings("name").isEmpty)
        assert(ConfigSupplier.of(Map("name" -> " ,  , ,  ")).getStrings("name").isEmpty)
      }
    }
    "fetching numeric values" should {
      "parse decimal values as integers" in {
        assert(ConfigSupplier.of(Map("channel" -> "0")).getInt("channel").contains(0))
        assert(ConfigSupplier.of(Map("channel" -> "5")).getInt("channel").contains(5))
        assert(ConfigSupplier.of(Map("channel" -> "39")).getInt("channel").contains(39))
      }
      "parse hex values as integers" in {
        assert(ConfigSupplier.of(Map("channel" -> "0x0")).getInt("channel").contains(0))
        assert(ConfigSupplier.of(Map("channel" -> "0x5")).getInt("channel").contains(5))
        assert(ConfigSupplier.of(Map("channel" -> "0x27")).getInt("channel").contains(39))
        assert(ConfigSupplier.of(Map("channel" -> "0x020")).getInt("channel").contains(32))
      }
      "invalid values result in None" in {
        assert(ConfigSupplier.of(Map()).getInt("channel").isEmpty)
        assert(ConfigSupplier.of(Map("channel" -> "")).getInt("channel").isEmpty)
        assert(ConfigSupplier.of(Map("channel" -> "null")).getInt("channel").isEmpty)
        assert(ConfigSupplier.of(Map("channel" -> "a")).getInt("channel").isEmpty)
        assert(ConfigSupplier.of(Map("channel" -> "a12")).getInt("channel").isEmpty)
        assert(ConfigSupplier.of(Map("channel" -> "z39")).getInt("channel").isEmpty)
        assert(ConfigSupplier.of(Map("channel" -> "12a")).getInt("channel").isEmpty)
        assert(ConfigSupplier.of(Map("channel" -> "39z")).getInt("channel").isEmpty)
        assert(ConfigSupplier.of(Map("channel" -> "10.0.0.13")).getInt("channel").isEmpty)
      }
    }
    "fetching boolean values" should {
      "parse toggle values" in {
        assert(ConfigSupplier.of(Map("t" -> "0")).getToggle("u").isEmpty)
        assert(ConfigSupplier.of(Map("t" -> "bob")).getToggle("t").isEmpty)

        assert(ConfigSupplier.of(Map("t" -> "0")).getToggle("t").contains(false))
        assert(ConfigSupplier.of(Map("t" -> "1")).getToggle("t").contains(true))

        assert(ConfigSupplier.of(Map("t" -> "n")).getToggle("t").contains(false))
        assert(ConfigSupplier.of(Map("t" -> "y")).getToggle("t").contains(true))
        assert(ConfigSupplier.of(Map("t" -> "no")).getToggle("t").contains(false))
        assert(ConfigSupplier.of(Map("t" -> "yes")).getToggle("t").contains(true))

        assert(ConfigSupplier.of(Map("t" -> "off")).getToggle("t").contains(false))
        assert(ConfigSupplier.of(Map("t" -> "on")).getToggle("t").contains(true))

        assert(ConfigSupplier.of(Map("t" -> "f")).getToggle("t").contains(false))
        assert(ConfigSupplier.of(Map("t" -> "t")).getToggle("t").contains(true))
        assert(ConfigSupplier.of(Map("t" -> "false")).getToggle("t").contains(false))
        assert(ConfigSupplier.of(Map("t" -> "true")).getToggle("t").contains(true))

        assert(ConfigSupplier.of(Map("t" -> "disable")).getToggle("t").contains(false))
        assert(ConfigSupplier.of(Map("t" -> "enable")).getToggle("t").contains(true))
        assert(ConfigSupplier.of(Map("t" -> "disabled")).getToggle("t").contains(false))
        assert(ConfigSupplier.of(Map("t" -> "enabled")).getToggle("t").contains(true))
      }
    }
    "fetching durations" should {
      "parse duration values" in {
        assert(ConfigSupplier.of(Map("d" -> "0")).getDuration("u").isEmpty)
        assert(ConfigSupplier.of(Map("d" -> "s0")).getDuration("d").isEmpty)
        assert(ConfigSupplier.of(Map("d" -> "zero")).getDuration("d").isEmpty)
        assert(ConfigSupplier.of(Map("d" -> "one-million-dollars")).getDuration("d").isEmpty)
        assert(ConfigSupplier.of(Map("d" -> "5y")).getDuration("d").isEmpty)

        assert(ConfigSupplier.of(Map("d" -> "0")).getDuration("d").contains(0.seconds))
        assert(ConfigSupplier.of(Map("d" -> "1")).getDuration("d").contains(1.second))
        assert(ConfigSupplier.of(Map("d" -> "2")).getDuration("d").contains(2.seconds))
        assert(ConfigSupplier.of(Map("d" -> "60")).getDuration("d").contains(60.seconds))

        assert(ConfigSupplier.of(Map("d" -> "0ns")).getDuration("d").contains(0.nanos))
        assert(ConfigSupplier.of(Map("d" -> "1ns")).getDuration("d").contains(1.nano))
        assert(ConfigSupplier.of(Map("d" -> "2ns")).getDuration("d").contains(2.nanos))
        assert(ConfigSupplier.of(Map("d" -> "100ns")).getDuration("d").contains(100.nanos))
        assert(ConfigSupplier.of(Map("d" -> "1000ns")).getDuration("d").contains(1000.nanos))

        assert(ConfigSupplier.of(Map("d" -> "0us")).getDuration("d").contains(0.micros))
        assert(ConfigSupplier.of(Map("d" -> "1us")).getDuration("d").contains(1.micro))
        assert(ConfigSupplier.of(Map("d" -> "2us")).getDuration("d").contains(2.micros))
        assert(ConfigSupplier.of(Map("d" -> "100us")).getDuration("d").contains(100.micros))
        assert(ConfigSupplier.of(Map("d" -> "1000us")).getDuration("d").contains(1000.micros))

        assert(ConfigSupplier.of(Map("d" -> "0ms")).getDuration("d").contains(0.millis))
        assert(ConfigSupplier.of(Map("d" -> "1ms")).getDuration("d").contains(1.milli))
        assert(ConfigSupplier.of(Map("d" -> "2ms")).getDuration("d").contains(2.millis))
        assert(ConfigSupplier.of(Map("d" -> "100ms")).getDuration("d").contains(100.millis))
        assert(ConfigSupplier.of(Map("d" -> "1000ms")).getDuration("d").contains(1000.millis))

        assert(ConfigSupplier.of(Map("d" -> "0s")).getDuration("d").contains(0.seconds))
        assert(ConfigSupplier.of(Map("d" -> "1s")).getDuration("d").contains(1.second))
        assert(ConfigSupplier.of(Map("d" -> "2s")).getDuration("d").contains(2.seconds))
        assert(ConfigSupplier.of(Map("d" -> "60s")).getDuration("d").contains(60.seconds))

        assert(ConfigSupplier.of(Map("d" -> "0m")).getDuration("d").contains(0.minutes))
        assert(ConfigSupplier.of(Map("d" -> "1m")).getDuration("d").contains(1.minute))
        assert(ConfigSupplier.of(Map("d" -> "2m")).getDuration("d").contains(2.minutes))
        assert(ConfigSupplier.of(Map("d" -> "60m")).getDuration("d").contains(60.minutes))

        assert(ConfigSupplier.of(Map("d" -> "0h")).getDuration("d").contains(0.hours))
        assert(ConfigSupplier.of(Map("d" -> "1h")).getDuration("d").contains(1.hour))
        assert(ConfigSupplier.of(Map("d" -> "2h")).getDuration("d").contains(2.hours))
        assert(ConfigSupplier.of(Map("d" -> "24h")).getDuration("d").contains(24.hours))

        assert(ConfigSupplier.of(Map("d" -> "0d")).getDuration("d").contains(0.days))
        assert(ConfigSupplier.of(Map("d" -> "1d")).getDuration("d").contains(1.day))
        assert(ConfigSupplier.of(Map("d" -> "2d")).getDuration("d").contains(2.days))
        assert(ConfigSupplier.of(Map("d" -> "30d")).getDuration("d").contains(30.days))
        assert(ConfigSupplier.of(Map("d" -> "365d")).getDuration("d").contains(365.days))
      }
    }
  }
}
