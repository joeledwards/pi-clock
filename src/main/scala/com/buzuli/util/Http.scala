package com.buzuli.util

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpHeader.ParsingResult.{Error, Ok}
import sttp.client._
import akka.http.scaladsl.{Http => AkkaHttp}
import akka.http.scaladsl.model._
import play.api.libs.json._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

sealed trait Validity[T]
case class Valid[T](value: T) extends Validity[T]
case class Invalid[T](message: String, input: T) extends Validity[T]

sealed trait HttpBody {
  def contentType: ContentType
}
case class HttpBodyText(body: String) extends HttpBody {
  val contentType = ContentTypes.`text/plain(UTF-8)`
}
case class HttpBodyBytes(body: Array[Byte]) extends HttpBody {
  val contentType = ContentTypes.`application/octet-stream`
}
case class HttpBodyJson(body: JsValue) extends HttpBody {
  val contentType = ContentTypes.`application/json`
}

sealed trait HttpResult {
  def response: Option[HttpResponse] = None
}
case class HttpResultInvalidMethod(input: String) extends HttpResult
case class HttpResultInvalidUrl(url: String) extends HttpResult
case class HttpResultInvalidHeader(name: String, value: String) extends HttpResult
case class HttpResultInvalidBody() extends HttpResult
case class HttpResultRawResponse(rawResponse: HttpResponse) extends HttpResult {
  override def response: Option[HttpResponse] = Some(rawResponse)
}

object Http {
  implicit val httpBackend = HttpURLConnectionBackend()
  implicit val system = ActorSystem("pi-clock")
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  object method {
    def apply(method: String): Validity[HttpMethod] = validateMethod(method)
  }

  object header {
    def apply(name: String, value: String): Validity[HttpHeader] = validateHeader((name, value))
  }

  object body {
    def text(body: String): HttpBody = HttpBodyText(body)
    def bytes(body: Array[Byte]): HttpBody = HttpBodyBytes(body)
    def json(body: JsValue): HttpBody = HttpBodyJson(body)
  }

  def http = AkkaHttp()

  def rq(request: HttpRequest): Future[HttpResponse] = http.singleRequest(request)
  def rq(
    method: String,
    url: String,
    headers: List[(String, String)] = Nil,
    body: Option[HttpBody] = None
  ): Future[HttpResult] = {
    (
      validateMethod(method),
      validateHeaders(headers)
    ) match {
      case (Invalid(_, _), _) => Future.successful(
        HttpResultInvalidMethod(method)
      )
      case (_, Invalid(_, inv :: _ :: Nil)) => Future.successful(
        HttpResultInvalidHeader(inv.name, inv.value)
      )
      case (Valid(mth), Valid(hdrs)) => rq(HttpRequest(
        method = mth,
        uri = url,
        headers = hdrs,
        entity = body match {
          case None => HttpEntity.Empty
          case Some(HttpBodyBytes(bytes)) => HttpEntity(bytes)
          case Some(HttpBodyText(text)) => HttpEntity(text)
          case Some(HttpBodyJson(json)) => HttpEntity(ContentTypes.`application/json`, json.toString.getBytes)
        }
      )) map {
        HttpResultRawResponse(_)
      }
    }
  }

  def get(
    url: String,
    headers: List[(String, String)] = Nil
  ): Future[HttpResult] = rq(
    "GET",
    url,
    headers = headers
  )

  def post(
    url: String,
    headers: List[(String, String)] = Nil,
    body: Option[HttpBody] = None
  ): Future[HttpResult] = rq(
    "POST",
    url,
    headers = headers,
    body = body
  )

  private def validateMethod(method: String): Validity[HttpMethod] = method.toUpperCase match {
    case "connect" => Valid(HttpMethods.CONNECT)
    case "delete" => Valid(HttpMethods.DELETE)
    case "get" => Valid(HttpMethods.GET)
    case "head" => Valid(HttpMethods.HEAD)
    case "options" => Valid(HttpMethods.OPTIONS)
    case "patch" => Valid(HttpMethods.PATCH)
    case "put" => Valid(HttpMethods.PUT)
    case "post" => Valid(HttpMethods.POST)
    case "trace" => Valid(HttpMethods.TRACE)
    case um => Invalid(s"Unsupported HTTP method \"$um\"", method)
  }

  private def validateHeaders(
    headers: List[(String, String)]
  ): Validity[List[HttpHeader]] = {
    val (invalids, valids) = headers map {
      validateHeader(_)
    } partition {
      case Valid(_) => true
      case Invalid(_, _) => false
    }

    invalids.length match {
      case 0 => Valid(valids)
      case n => Invalid(s"Found ${n} invalid headers.", headers)
    }
  }

  private def validateHeader(
    header: (String, String)
  ): Validity[HttpHeader] = HttpHeader.parse(header._1, header._2) match {
    case Ok(header, errors) => Valid(header)
    case Error(error) => Invalid(error.detail, header)
  }

  def shutdown(): Unit = system.terminate()
}

object TryHttp extends App {
  val result = Await.result(Http.get("http://rocket:1337/"), Duration(5, TimeUnit.SECONDS))

  result match {
    case HttpResultRawResponse(response) => {
      val json = Json.obj(
        "status" -> response.status.intValue,
        "headers" -> Json.arr(response.headers.map(h =>
          h.name -> h.value
        ))
      )
      println(Json.prettyPrint(json))
    }
    case _ => println(s"Invalid request!")
  }

  Http.shutdown()
}
