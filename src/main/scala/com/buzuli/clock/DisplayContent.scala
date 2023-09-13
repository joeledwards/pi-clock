package com.buzuli.clock

import com.buzuli.util.{Strings, SysInfo}

import java.time.{Instant, ZoneId, ZoneOffset}

object DisplayContent {
  def getDisplayLines(
    timestamp: Instant,
    internetHealth: Option[InternetHealth],
    displayContent: DisplayContent
  ): List[Option[String]] = {
    Config.displayDimensions match {
      case Display20x4 => getLinesFor20x4(timestamp, internetHealth)
      case Display16x2 => getLinesFor16x2(timestamp, displayContent)
    }
  }

  private def getLinesFor20x4(timestamp: Instant, internetHealth: Option[InternetHealth]): List[Option[String]] = {
    val tsLocal = timestamp.atZone(ZoneId.systemDefault)
    val tsUtc = timestamp.atZone(ZoneOffset.UTC)

    val utcTimeString = (Config.binary, Config.humanFriendly) match {
      case (true, _) => {
        val utcHour = Strings.padLeft('0', 5)(tsUtc.getHour.toBinaryString)
        val utcMinute = Strings.padLeft('0', 6)(tsUtc.getMinute.toBinaryString)
        val utcSecond = Strings.padLeft('0', 6)(tsUtc.getSecond.toBinaryString)
        s"${utcHour}:${utcMinute}.${utcSecond}Z"
      }
      case (_, true) => s"${timestamp.toString.slice(0, 16).replace('T', ' ')} < Z"
      case _ => s"${timestamp.toString.slice(0, 19)}Z"
    }
    val localTs = timestamp.atZone(ZoneId.systemDefault)
    val localTimeString = (Config.binary, Config.humanFriendly) match {
      case (true, _) => {
        val localHour = Strings.padLeft('0', 5)(tsLocal.getHour.toBinaryString)
        val localMinute = Strings.padLeft('0', 6)(tsLocal.getMinute.toBinaryString)
        val localSecond = Strings.padLeft('0', 6)(tsLocal.getSecond.toBinaryString)
        s"${localHour}:${localMinute}.${localSecond}L"
      }
      case (_, true) => s"${localTs.toString.slice(0, 16).replace('T', ' ')} < L"
      case _ => s"${localTs.toString.slice(0, 19)}L"
    }

    val host = SysInfo.host.value.getOrElse("--")
    val ipString = (Config.binary, Config.humanFriendly, SysInfo.ip.value.getOrElse("--")) match {
      case (false, true, ip) => {
        val seconds = timestamp.toString.slice(17, 19)
        val padding = " " * 20
        val ipPadded = s"${ip}${padding}"
        val separator = {
          if (internetHealth.exists(_.isNetworkDown)) {
            "N"
          } else if (internetHealth.exists(_.isInternetDown)) {
            "X"
          } else {
            "|"
          }
        }
        s"${ipPadded.slice(0, 15)} ${separator} ${seconds}"
      }
      case (_, _, ip) => ip
    }

    Some(localTimeString) ::
      Some(utcTimeString) ::
      Some(host) ::
      Some(ipString) ::
      Nil
  }

  private def getLinesFor16x2(timestamp: Instant, displayContent: DisplayContent): List[Option[String]] = {
    val tsLocal = timestamp.atZone(ZoneId.systemDefault)
    val tsUtc = timestamp.atZone(ZoneOffset.UTC)
    val utcTimeString = s"${tsUtc.toString.slice(0, 16).replace('T', ' ')}"
    val localTimeString = s"${tsLocal.toString.slice(0, 16).replace('T', ' ')}"
    val ipString = SysInfo.ip.value.getOrElse("--")
    val host = SysInfo.host.value.getOrElse("--")

    val utcHour = Strings.padLeft('0', 5)(tsUtc.getHour.toBinaryString)
    val utcMinute = Strings.padLeft('0', 6)(tsUtc.getMinute.toBinaryString)
    val utcBinaryTime = s"${utcHour}:${utcMinute} > Z"

    val localHour = Strings.padLeft('0', 5)(tsLocal.getHour.toBinaryString)
    val localMinute = Strings.padLeft('0', 6)(tsLocal.getMinute.toBinaryString)
    val localBinaryTime = s"${localHour}:${localMinute} > L"

    displayContent match {
      case DisplayUtcAndHost => Some(utcTimeString) :: Some(host) :: Nil
      case DisplayLocalAndHost => Some(localTimeString) :: Some(host) :: Nil
      case DisplayUtcAndIp => Some(utcTimeString) :: Some(ipString) :: Nil
      case DisplayLocalAndIp => Some(localTimeString) :: Some(ipString) :: Nil
      case DisplayTimesUtcTop => Some(utcTimeString) :: Some(localTimeString) :: Nil
      case DisplayTimesLocalTop => Some(localTimeString) :: Some(utcTimeString) :: Nil
      case DisplayBinaryTimeUtc => Some(utcBinaryTime) :: Some(ipString) :: Nil
      case DisplayBinaryTimeLocal => Some(localBinaryTime) :: Some(ipString) :: Nil
    }
  }
}

sealed trait DisplayContent {
  def next: DisplayContent
}

case object DisplayUtcAndHost extends DisplayContent {
  override def next: DisplayContent = DisplayLocalAndHost
}
case object DisplayLocalAndHost extends DisplayContent {
  override def next: DisplayContent = DisplayUtcAndIp
}
case object DisplayUtcAndIp extends DisplayContent {
  override def next: DisplayContent = DisplayLocalAndIp
}
case object DisplayLocalAndIp extends DisplayContent {
  override def next: DisplayContent = DisplayTimesUtcTop
}
case object DisplayTimesUtcTop extends DisplayContent {
  override def next: DisplayContent = DisplayTimesLocalTop
}
case object DisplayTimesLocalTop extends DisplayContent {
  override def next: DisplayContent = DisplayBinaryTimeUtc
}
case object DisplayBinaryTimeUtc extends DisplayContent {
  override def next: DisplayContent = DisplayBinaryTimeLocal
}
case object DisplayBinaryTimeLocal extends DisplayContent {
  override def next: DisplayContent = DisplayUtcAndHost
}
