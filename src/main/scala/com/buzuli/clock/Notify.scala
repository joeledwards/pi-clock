package com.buzuli.clock

import com.buzuli.util.{Http, HttpBodyJson, HttpResultInvalidBody, HttpResultInvalidHeader, HttpResultInvalidMethod, HttpResultInvalidUrl, HttpResultRawResponse}
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

object Notify {
  def slack(message: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    Config.notificationSlackWebhook match {
      case None => Future.successful(false)
      case Some(webhook) => Http.post(webhook, body = Some(HttpBodyJson(Json.obj(
        "text" -> message
      )))) map {
        _ match {
          case HttpResultInvalidMethod(method) => {
            println(s"""[Slack] Invalid method "$method"""")
            false
          }
          case HttpResultInvalidUrl(url) => {
            println(s"""[Slack] Invalid URL "$url" """)
            false
          }
          case HttpResultInvalidHeader(name, value) => {
            println(s"""[Slack] Invalid header "$name=$value"""")
            false
          }
          case HttpResultInvalidBody() => {
            println(s"""[Slack] Invalid message body""")
            false
          }
          case HttpResultRawResponse(response, None) => {
            val (result, outcome) = response.status.intValue match {
              case 200 => (true, "succeeded")
              case _ => (false, "failed")
            }
            println(s"""[Slack] Send ${outcome}""")
            result
          }
        }
      }
    }
  }
}
