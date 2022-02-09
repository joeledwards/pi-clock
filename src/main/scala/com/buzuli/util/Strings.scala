package com.buzuli.util

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
}
