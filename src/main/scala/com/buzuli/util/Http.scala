package com.buzuli.util

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import sttp.client._
import akka.http.scaladsl.{Http => AkkaHttp}
import akka.http.scaladsl.model._
import play.api.libs.json._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

object Http {
  implicit val httpBackend = HttpURLConnectionBackend()
  implicit val system = ActorSystem("pi-clock")
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def http = AkkaHttp()

  def rq(request: HttpRequest): Future[HttpResponse] = http.singleRequest(request)
  def get(url: String): Future[HttpResponse] = rq(HttpRequest(
    method = HttpMethods.GET,
    uri = url
  ))

  def shutdown(): Unit = system.terminate()
}

object TryHttp extends App {
  val response = Await.result(Http.get("http://rocket:1337/"), Duration(5, TimeUnit.SECONDS))
  val json = Json.obj(
    "status" -> response.status.intValue,
    "headers" -> Json.obj()
  )
  println(Json.prettyPrint(json))

  Http.shutdown()
}
