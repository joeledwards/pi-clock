package com.buzuli.util

import com.buzuli.UnitSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._


class ConfigSpec extends UnitSpec {
  "ConfigSupplier" when {
    "fetching string values" should {
      "return Some when found" in {
        assert(ConfigSupplier.of(Map("name" -> "")).get("name") == Some(""))
        assert(ConfigSupplier.of(Map("name" -> "buzuli")).get("name") == Some("buzuli"))
      }
      "return None when not found" in {
        assert(ConfigSupplier.of(Map("nay" -> "buzuli")).get("name") == None)
      }
    }
    "fetching numeric values" should {
      "parse decimal values as integers" in {
        assert(ConfigSupplier.of(Map("channel" -> "0")).getInt("channel") == Some(0))
        assert(ConfigSupplier.of(Map("channel" -> "5")).getInt("channel") == Some(5))
        assert(ConfigSupplier.of(Map("channel" -> "39")).getInt("channel") == Some(39))
      }
      "parse hex values as integers" in {
        assert(ConfigSupplier.of(Map("channel" -> "0x0")).getInt("channel") == Some(0))
        assert(ConfigSupplier.of(Map("channel" -> "0x5")).getInt("channel") == Some(5))
        assert(ConfigSupplier.of(Map("channel" -> "0x27")).getInt("channel") == Some(39))
        assert(ConfigSupplier.of(Map("channel" -> "0x020")).getInt("channel") == Some(32))
      }
      "invalid values result in None" in {
        assert(ConfigSupplier.of(Map()).getInt("channel") == None)
        assert(ConfigSupplier.of(Map("channel" -> "")).getInt("channel") == None)
        assert(ConfigSupplier.of(Map("channel" -> "null")).getInt("channel") == None)
        assert(ConfigSupplier.of(Map("channel" -> "a")).getInt("channel") == None)
        assert(ConfigSupplier.of(Map("channel" -> "a12")).getInt("channel") == None)
        assert(ConfigSupplier.of(Map("channel" -> "z39")).getInt("channel") == None)
        assert(ConfigSupplier.of(Map("channel" -> "12a")).getInt("channel") == None)
        assert(ConfigSupplier.of(Map("channel" -> "39z")).getInt("channel") == None)
        assert(ConfigSupplier.of(Map("channel" -> "10.0.0.13")).getInt("channel") == None)
      }
    }
    "fetching boolean values" should {
      "parse toggle values" in {
        assert(ConfigSupplier.of(Map("t" -> "0")).getToggle("u") == None)
        assert(ConfigSupplier.of(Map("t" -> "bob")).getToggle("t") == None)

        assert(ConfigSupplier.of(Map("t" -> "0")).getToggle("t") == Some(false))
        assert(ConfigSupplier.of(Map("t" -> "1")).getToggle("t") == Some(true))

        assert(ConfigSupplier.of(Map("t" -> "n")).getToggle("t") == Some(false))
        assert(ConfigSupplier.of(Map("t" -> "y")).getToggle("t") == Some(true))
        assert(ConfigSupplier.of(Map("t" -> "no")).getToggle("t") == Some(false))
        assert(ConfigSupplier.of(Map("t" -> "yes")).getToggle("t") == Some(true))

        assert(ConfigSupplier.of(Map("t" -> "off")).getToggle("t") == Some(false))
        assert(ConfigSupplier.of(Map("t" -> "on")).getToggle("t") == Some(true))

        assert(ConfigSupplier.of(Map("t" -> "f")).getToggle("t") == Some(false))
        assert(ConfigSupplier.of(Map("t" -> "t")).getToggle("t") == Some(true))
        assert(ConfigSupplier.of(Map("t" -> "false")).getToggle("t") == Some(false))
        assert(ConfigSupplier.of(Map("t" -> "true")).getToggle("t") == Some(true))

        assert(ConfigSupplier.of(Map("t" -> "disable")).getToggle("t") == Some(false))
        assert(ConfigSupplier.of(Map("t" -> "enable")).getToggle("t") == Some(true))
        assert(ConfigSupplier.of(Map("t" -> "disabled")).getToggle("t") == Some(false))
        assert(ConfigSupplier.of(Map("t" -> "enabled")).getToggle("t") == Some(true))
      }
    }
    "fetching durations" should {
      "parse duration values" in {
        assert(ConfigSupplier.of(Map("d" -> "0")).getDuration("u") == None)
        assert(ConfigSupplier.of(Map("d" -> "s0")).getDuration("d") == None)
        assert(ConfigSupplier.of(Map("d" -> "zero")).getDuration("d") == None)
        assert(ConfigSupplier.of(Map("d" -> "one-million-dollars")).getDuration("d") == None)
        assert(ConfigSupplier.of(Map("d" -> "5y")).getDuration("d") == None)

        assert(ConfigSupplier.of(Map("d" -> "0")).getDuration("d") == Some(0.seconds))
        assert(ConfigSupplier.of(Map("d" -> "1")).getDuration("d") == Some(1.second))
        assert(ConfigSupplier.of(Map("d" -> "2")).getDuration("d") == Some(2.seconds))
        assert(ConfigSupplier.of(Map("d" -> "60")).getDuration("d") == Some(60.seconds))

        assert(ConfigSupplier.of(Map("d" -> "0ns")).getDuration("d") == Some(0.nanos))
        assert(ConfigSupplier.of(Map("d" -> "1ns")).getDuration("d") == Some(1.nano))
        assert(ConfigSupplier.of(Map("d" -> "2ns")).getDuration("d") == Some(2.nanos))
        assert(ConfigSupplier.of(Map("d" -> "100ns")).getDuration("d") == Some(100.nanos))
        assert(ConfigSupplier.of(Map("d" -> "1000ns")).getDuration("d") == Some(1000.nanos))

        assert(ConfigSupplier.of(Map("d" -> "0us")).getDuration("d") == Some(0.micros))
        assert(ConfigSupplier.of(Map("d" -> "1us")).getDuration("d") == Some(1.micro))
        assert(ConfigSupplier.of(Map("d" -> "2us")).getDuration("d") == Some(2.micros))
        assert(ConfigSupplier.of(Map("d" -> "100us")).getDuration("d") == Some(100.micros))
        assert(ConfigSupplier.of(Map("d" -> "1000us")).getDuration("d") == Some(1000.micros))

        assert(ConfigSupplier.of(Map("d" -> "0ms")).getDuration("d") == Some(0.millis))
        assert(ConfigSupplier.of(Map("d" -> "1ms")).getDuration("d") == Some(1.milli))
        assert(ConfigSupplier.of(Map("d" -> "2ms")).getDuration("d") == Some(2.millis))
        assert(ConfigSupplier.of(Map("d" -> "100ms")).getDuration("d") == Some(100.millis))
        assert(ConfigSupplier.of(Map("d" -> "1000ms")).getDuration("d") == Some(1000.millis))

        assert(ConfigSupplier.of(Map("d" -> "0s")).getDuration("d") == Some(0.seconds))
        assert(ConfigSupplier.of(Map("d" -> "1s")).getDuration("d") == Some(1.second))
        assert(ConfigSupplier.of(Map("d" -> "2s")).getDuration("d") == Some(2.seconds))
        assert(ConfigSupplier.of(Map("d" -> "60s")).getDuration("d") == Some(60.seconds))

        assert(ConfigSupplier.of(Map("d" -> "0m")).getDuration("d") == Some(0.minutes))
        assert(ConfigSupplier.of(Map("d" -> "1m")).getDuration("d") == Some(1.minute))
        assert(ConfigSupplier.of(Map("d" -> "2m")).getDuration("d") == Some(2.minutes))
        assert(ConfigSupplier.of(Map("d" -> "60m")).getDuration("d") == Some(60.minutes))

        assert(ConfigSupplier.of(Map("d" -> "0h")).getDuration("d") == Some(0.hours))
        assert(ConfigSupplier.of(Map("d" -> "1h")).getDuration("d") == Some(1.hour))
        assert(ConfigSupplier.of(Map("d" -> "2h")).getDuration("d") == Some(2.hours))
        assert(ConfigSupplier.of(Map("d" -> "24h")).getDuration("d") == Some(24.hours))

        assert(ConfigSupplier.of(Map("d" -> "0d")).getDuration("d") == Some(0.days))
        assert(ConfigSupplier.of(Map("d" -> "1d")).getDuration("d") == Some(1.day))
        assert(ConfigSupplier.of(Map("d" -> "2d")).getDuration("d") == Some(2.days))
        assert(ConfigSupplier.of(Map("d" -> "30d")).getDuration("d") == Some(30.days))
        assert(ConfigSupplier.of(Map("d" -> "365d")).getDuration("d") == Some(365.days))
      }
    }
  }
}
