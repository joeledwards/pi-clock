package com.buzuli.util

import java.nio.charset.StandardCharsets
import java.nio.charset.Charset
import java.io.PrintStream
import java.io.ByteArrayOutputStream

object Strings {
  def padLeft(pad: Char, minLength: Int)(text: String): String = {
    if (text.length >= minLength) {
      text
    } else {
      val deficit = minLength - text.length
      s"${pad.toString * deficit}${text}"
    }
  }

  def padRight(pad: Char, minLength: Int)(text: String): String = {
    if (text.length >= minLength) {
      text
    } else {
      val deficit = minLength - text.length
      s"${text}${pad.toString * deficit}"
    }
  }

  def stream(
    input: PrintStream => Unit,
    charset: Charset = StandardCharsets.UTF_8
  ): String = {
    val buffer = new ByteArrayOutputStream
    val writer = new PrintStream(buffer)
    input(writer)
    buffer.toString(charset)
  }
}
