package com.buzuli

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ConfigSpec extends AnyWordSpec with Matchers {
  "ConfigSupplier" when {
    "fetching string values" should {
      "return Some when found" in {
        assert(ConfigSupplier.of(Map("name" -> "buzuli")).get("name") == Some("buzuli"))
      }
      "return None when not found" in {
        assert(ConfigSupplier.of(Map("nay" -> "buzuli")).get("name") == None)
      }
    }
    "fetching numeric values" should {
      "parse decimal values as integers" in {
        assert(ConfigSupplier.of(Map("channel" -> "39")).getInt("channel") == Some(39))
      }
      "parse hex values as integers" in {
        assert(ConfigSupplier.of(Map("channel" -> "0x27")).getInt("channel") == Some(39))
      }
    }
    "fetching boolean values" should {
      "parse toggle values" in {
        assert(ConfigSupplier.of(Map("t" -> "0")).getToggle("u") == None)
        assert(ConfigSupplier.of(Map("t" -> "0")).getToggle("t") == Some(false))
        assert(ConfigSupplier.of(Map("t" -> "1")).getToggle("t") == Some(true))
        assert(ConfigSupplier.of(Map("t" -> "no")).getToggle("t") == Some(false))
        assert(ConfigSupplier.of(Map("t" -> "yes")).getToggle("t") == Some(true))
        assert(ConfigSupplier.of(Map("t" -> "off")).getToggle("t") == Some(false))
        assert(ConfigSupplier.of(Map("t" -> "on")).getToggle("t") == Some(true))
        assert(ConfigSupplier.of(Map("t" -> "false")).getToggle("t") == Some(false))
        assert(ConfigSupplier.of(Map("t" -> "true")).getToggle("t") == Some(true))
      }
    }
  }
}
