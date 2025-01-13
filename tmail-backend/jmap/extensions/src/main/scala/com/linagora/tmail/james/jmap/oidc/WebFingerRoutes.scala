/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.jmap.oidc

import java.net.{URL, URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets
import java.util.stream

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Provides}
import com.linagora.tmail.james.jmap.oidc.WebFingerRoutes.LOGGER
import com.linagora.tmail.james.jmap.{JMAPExtensionConfiguration, WebFingerConfiguration}
import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.HttpResponseStatus.{BAD_REQUEST, INTERNAL_SERVER_ERROR}
import io.netty.handler.codec.http.{HttpMethod, HttpResponseStatus, QueryStringDecoder}
import jakarta.inject.Inject
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.core.ProblemDetails
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsString, Json, Writes}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Try}

case class WebFingerLink(rel: URL, href: URL)
case class WebFingerResponse(subject: URL, links: Seq[WebFingerLink])

private[oidc] object Serializers {
  private implicit val stateWrites: Writes[URL] = url => JsString(url.toString)
  private implicit val linkWrites: Writes[WebFingerLink] = Json.writes[WebFingerLink]
  private implicit val responseWrites: Writes[WebFingerResponse] = Json.writes[WebFingerResponse]

  def serialise(response: WebFingerResponse): String = Json.stringify(Json.toJson(response))
}

case class WebFingerRequest(resource: URL)

case class WebFingerModule() extends AbstractModule {
  override def configure(): Unit = {
    val routes = Multibinder.newSetBinder(binder, classOf[JMAPRoutes])
    routes.addBinding().to(classOf[WebFingerRoutes])
  }

  @Provides
  def configuration(jmapExtensionConfiguration: JMAPExtensionConfiguration): WebFingerConfiguration =
    jmapExtensionConfiguration.webFingerConfiguration
}

object WebFingerRoutes {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[WebFingerRoutes])
}

class WebFingerRoutes @Inject()(configuration: WebFingerConfiguration) extends JMAPRoutes {
  private val ENDPOINT: String = ".well-known/webfinger"
  private val REL = new URL("http://openid.net/specs/connect/1.0/issuer")

  override def routes(): stream.Stream[JMAPRoute] =
    configuration.openIdUrl.map(url => stream.Stream.of(
      JMAPRoute.builder
        .endpoint(new Endpoint(HttpMethod.GET, s"/$ENDPOINT"))
        .action((req, res) => this.generate(url)(req, res))
        .corsHeaders,
      corsEndpoint))
      .getOrElse(stream.Stream.of(corsEndpoint))

  private def corsEndpoint: JMAPRoute =
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, s"/$ENDPOINT"))
      .action(JMAPRoutes.CORS_CONTROL)
      .noCorsHeaders

  private def generate(openIdURL: URL)(request: HttpServerRequest, response: HttpServerResponse): Mono[Void] =
    asWebFingerRequest(
      queryParam(request, "resource"),
      queryParam(request, "rel"))
      .map(toWebFingerResponse(_, openIdURL))
      .fold(e => handleException(e, response),
        res => response
          .status(HttpResponseStatus.OK)
          .header(CONTENT_TYPE, "application/jrd+json")
          .sendString(SMono.just(Serializers.serialise(res)))
          .`then`())

  def asWebFingerRequest(resource: Option[String], rel: Option[String]): Try[WebFingerRequest] = (resource, rel) match {
    case (None, _) => Failure(new IllegalArgumentException("'resource' query parameter is compulsory"))
    case (_, None) => Failure(new IllegalArgumentException("'rel' query parameter is compulsory"))
    case (Some(resource), Some("http://openid.net/specs/connect/1.0/issuer")) =>
      Try(WebFingerRequest(new URL(URLDecoder.decode(resource, StandardCharsets.UTF_8))))
    case _ => Failure(new IllegalArgumentException(s"'rel' supports only '$REL' (URL encoded: ${URLEncoder.encode(REL.toString, StandardCharsets.UTF_8)})"))
  }

  def toWebFingerResponse(request: WebFingerRequest, openIdUrl: URL): WebFingerResponse =
    WebFingerResponse(subject = request.resource,
      links = List(WebFingerLink(
        rel = REL,
        href = openIdUrl)))

  private def queryParam(httpRequest: HttpServerRequest, parameterName: String): Option[String] =
    queryParam(parameterName, httpRequest.uri)

  private def queryParam(parameterName: String, uri: String): Option[String] =
    Option(new QueryStringDecoder(uri).parameters.get(parameterName))
      .toList
      .flatMap(_.asScala)
      .headOption

  private def handleException(throwable: Throwable, response: HttpServerResponse): Mono[Void] = throwable match {
    case e: IllegalArgumentException =>
      LOGGER.debug("WebFinger: Bad request", e)
      respondDetails(response,
        ProblemDetails(status = BAD_REQUEST, detail = e.getMessage),
        BAD_REQUEST)
    case e =>
      LOGGER.error("Unexpected error upon WebFinger resolution", e)
      respondDetails(response,
        ProblemDetails(status = INTERNAL_SERVER_ERROR, detail = e.getMessage),
        INTERNAL_SERVER_ERROR)
  }

  private def respondDetails(httpServerResponse: HttpServerResponse, details: ProblemDetails, statusCode: HttpResponseStatus = BAD_REQUEST): Mono[Void] =
    httpServerResponse.status(statusCode)
      .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
      .sendString(SMono.fromCallable(() => ResponseSerializer.serialize(details).toString), StandardCharsets.UTF_8)
      .`then`
}