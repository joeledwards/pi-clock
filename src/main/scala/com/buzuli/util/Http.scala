package com.buzuli.util

import java.time.Instant
import java.util.concurrent.TimeUnit
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json._
import sttp.capabilities.akka.AkkaStreams
import sttp.{capabilities, client3 => http}
import sttp.client3.{Response, SttpBackend, SttpBackendOptions}
import sttp.client3.akkahttp.AkkaHttpBackend
import sttp.model.{Header, MediaType, Method, Uri}

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

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
  implicit val httpOptions: SttpBackendOptions = SttpBackendOptions.connectionTimeout(5.seconds)
  implicit val httpBackend: SttpBackend[Future, AkkaStreams with capabilities.WebSockets] = AkkaHttpBackend(httpOptions)
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
    timeout: Duration = 15.seconds
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
      case (u, m, h) => {
        throw new Exception(s"Uncategorized, invalid HTTP configuration.\nMethod => ${m}\nURL => ${u}\nHeaders => ${h}")
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
    val (valids, invalids) = headers map {
      validateHeader
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

object TryHttp extends App with LazyLogging {
  val method = "GET"
  val url = "http://rocket:1337/question?name=bob"
  val headers: List[(String, String)] = List("x-note" -> "test")
  val timeout = Duration(3, TimeUnit.SECONDS)

  Try {
    Await.result(
      Http.rq(method = method, url = url, headers = headers, timeout = timeout),
      Duration(5, TimeUnit.SECONDS)
    )
  } match {
    case Failure(error) => {
      logger.error("Error in HTTP request.", error)
    }
    case Success(rsp@HttpResultRawResponse(response, body, elapsed)) => {
      val json = JsObject(
        Seq(
          "timeout" -> JsString(timeout.toString),
          "elapsed" -> elapsed.map(e => JsString(e.toString)).getOrElse(JsNull),
          "request" -> JsObject(
            Seq(
              "method" -> JsString(method),
              "url" -> JsString(url),
              "headers" -> JsObject(
                headers.map(e => (e._1, JsString(e._2)))
              )
            )
          ),
          "response" -> JsObject(
            Seq(
              "status" -> rsp.status.map(sc => JsNumber(sc)).getOrElse(JsNull),
              "headers" -> JsObject(
                response.headers.map(h => h.name -> JsString(h.value))
              ),
              "body" -> (body match {
                case None => JsNull
                case Some(HttpBodyJson(json)) => json
                case Some(HttpBodyBytes(bytes)) => {
                  Try {
                    Json.parse(bytes)
                  } match {
                    case Success(json) => json
                    case Failure(_) => JsString(new String(bytes))
                  }
                }
                case Some(HttpBodyText(text)) => {
                  Try {
                    Json.parse(text)
                  } match {
                    case Success(json) => json
                    case Failure(_) => JsString(text)
                  }
                }
              })
            )
          )
        )
      )
      logger.info(s"JSON Payload:\n${Json.prettyPrint(json)}")
    }
    case _ => logger.warn(s"Invalid request!")
  }

  Http.shutdown()
}
