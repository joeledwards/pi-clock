package com.buzuli.util

import java.time.Instant
import java.util.concurrent.TimeUnit
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import play.api.libs.json._
import sttp.{client3 => http}
import sttp.client3.{Response, SttpBackendOptions}
import sttp.client3.akkahttp.AkkaHttpBackend
import sttp.model.{Header, MediaType, Method, Uri}

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContext, Future}

sealed trait Validity[I, O]
case class Valid[I, O](value: O) extends Validity[I, O]
case class Invalid[I, O](message: String, input: I) extends Validity[I, O]

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
  def response: Option[Response[Either[String, String]]] = None
  def status: Option[Int] = None
  def body: Option[HttpBody] = None
  def duration: Option[Duration] = None
}
case class HttpResultInvalidMethod(input: String) extends HttpResult
case class HttpResultInvalidUrl(url: String) extends HttpResult
case class HttpResultInvalidHeader(name: String, value: String) extends HttpResult
case class HttpResultInvalidBody() extends HttpResult
case class HttpResultRawResponse(
    rawResponse: Response[Either[String, String]],
    data: Option[HttpBody] = None,
    elapsed: Option[Duration] = None
) extends HttpResult {
  override def response: Some[Response[Either[String, String]]] = Some(rawResponse)
  override def status: Option[Int] = response.map(_.code.code)
  override def body: Option[HttpBody] = data
  override def duration: Option[Duration] = elapsed
}

object Http {
  implicit val system: ActorSystem = ActorSystem("pi-clock")
  implicit val httpOptions = SttpBackendOptions.connectionTimeout(5.seconds)
  implicit val httpBackend = AkkaHttpBackend(httpOptions)
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  object method {
    def apply(method: String): Validity[String, Method] = validateMethod(method)
  }

  object header {
    def apply(name: String, value: String): Validity[(String, String), Header] = {
      validateHeader((name, value))
    }
  }

  object body {
    def text(body: String): HttpBody = HttpBodyText(body)
    def bytes(body: Array[Byte]): HttpBody = HttpBodyBytes(body)
    def json(body: JsValue): HttpBody = HttpBodyJson(body)
  }

  def rq(
    method: String,
    url: String,
    headers: List[(String, String)] = Nil,
    body: Option[HttpBody] = None,
    timeout: Duration = 15.minutes
  ): Future[HttpResult] = {
    val start: Instant = Instant.now

    (
      Uri.parse(url),
      validateMethod(method),
      validateHeaders(headers)
    ) match {
      case (Left(_), _, _) => Future.successful(
        HttpResultInvalidUrl(url)
      )
      case (_, Invalid(_, _), _) => Future.successful(
        HttpResultInvalidMethod(method)
      )
      case (_, _, Invalid(_, inv :: _ :: Nil)) => Future.successful(
        HttpResultInvalidHeader(inv._1, inv._2)
      )
      case (Right(uri), Valid(method), Valid(headerList)) => {
        val headers = headerList.map(h => (h.name, h.value)).toMap
        val baseRequest = http.basicRequest
          .readTimeout(timeout)
          .method(method, uri)
          .headers(headers)

        val httpRequest = body match {
          case None => baseRequest
          case Some(HttpBodyBytes(bytes)) => baseRequest.contentType(MediaType.ApplicationOctetStream).body(bytes)
          case Some(HttpBodyJson(json)) => baseRequest.contentType(MediaType.ApplicationJson).body(json.toString)
          case Some(HttpBodyText(text)) => baseRequest.contentType(MediaType.TextPlain).body(text)
        }

        httpBackend.send(httpRequest) map { response =>
          val statusCode = response.code.code
          val elapsed = Some(Time.diff(start, Instant.now))
          val data = response.body match {
            case Left(errorMessage) => HttpBodyText(errorMessage)
            case Right(data) => response.contentType match {
              case Some(MediaType.ApplicationJson.mainType) => HttpBodyJson(Json.parse(data))
              case Some(MediaType.TextPlain.mainType) => HttpBodyText(data)
              case _ => HttpBodyBytes(data.getBytes)
            }
          }

          HttpResultRawResponse(response, Some(data), elapsed)
        }
      }
      case (_, _, _) => {
        throw new Exception("Uncategorized, invalid HTTP configuration.")
      }
    }
  }

  def get(
    url: String,
    headers: List[(String, String)] = Nil,
    timeout: Duration = 15.seconds
  ): Future[HttpResult] = rq(
    "GET",
    url,
    headers = headers,
    timeout = timeout
  )

  def post(
    url: String,
    headers: List[(String, String)] = Nil,
    body: Option[HttpBody] = None,
    timeout: Duration = 15.seconds
  ): Future[HttpResult] = rq(
    "POST",
    url,
    headers = headers,
    body = body,
    timeout = timeout
  )

  private def validateMethod(method: String): Validity[String, Method] = method.toUpperCase match {
    case "CONNECT" => Valid(Method.CONNECT)
    case "DELETE" => Valid(Method.DELETE)
    case "GET" => Valid(Method.GET)
    case "HEAD" => Valid(Method.HEAD)
    case "OPTIONS" => Valid(Method.OPTIONS)
    case "PATCH" => Valid(Method.PATCH)
    case "PUT" => Valid(Method.PUT)
    case "POST" => Valid(Method.POST)
    case "TRACE" => Valid(Method.TRACE)
    case um => Invalid(s"""Unsupported HTTP method "$um"""", method)
  }

  private def validateHeaders(
    headers: List[(String, String)]
  ): Validity[List[(String, String)], List[Header]] = {
    val (invalids, valids) = headers map {
      validateHeader(_)
    } partition {
      case Valid(_) => true
      case Invalid(_, _) => false
    }

    invalids.length match {
      case 0 => Valid(valids collect { case Valid(header) => header })
      case n => Invalid(s"Found ${n} invalid headers.", headers)
    }
  }

  private def validateHeader(
    header: (String, String)
  ): Validity[(String, String), Header] = Header.safeApply(header._1, header._2) match {
    case Left(errorMessage) => Invalid(errorMessage, header)
    case Right(header) => Valid(header)
  }

  def shutdown(): Unit = {
    httpBackend.close()
    system.terminate()
  }
}

object TryHttp extends App {
  val result = Await.result(Http.get("http://rocket:1337/"), Duration(5, TimeUnit.SECONDS))

  result match {
    case HttpResultRawResponse(response, _, _) => {
      val json = Json.obj(
        "status" -> response.code.code,
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
