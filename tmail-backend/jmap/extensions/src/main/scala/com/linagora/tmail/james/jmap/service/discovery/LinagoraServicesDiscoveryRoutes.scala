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

package com.linagora.tmail.james.jmap.service.discovery

import java.util.stream.Stream

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.linagora.tmail.james.jmap.service.discovery.LinagoraServicesDiscoveryRoutes.LOGGER
import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.{HttpMethod, HttpResponseStatus}
import jakarta.inject.{Inject, Named}
import org.apache.commons.lang3.StringUtils
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.core.ProblemDetails
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.Authenticator
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.apache.james.utils.PropertiesProvider
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsObject, JsString, JsValue, Json}
import reactor.core.publisher.Mono
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

private[jmap] object ServicesDiscoveryConfigurationSerializers {
  private val NESTED_DELIMITER = "\\."
  private val UNDERSCORE_DELIMITER = "_"

  def serialize(response: LinagoraServicesDiscoveryConfiguration): String =
    Json.stringify(response.services.foldLeft(Json.obj()) { (json, service) =>
      insertNestedJson(json,
        service.key.split(NESTED_DELIMITER).map(_.replace(UNDERSCORE_DELIMITER, StringUtils.SPACE)).toList,
        JsString(service.value))
    })

  private def insertNestedJson(base: JsObject, path: List[String], value: JsValue): JsObject =
    path match {
      case head :: Nil => base + (head -> value)
      case head :: tail => base + (head -> insertNestedJson((base \ head).asOpt[JsObject].getOrElse(Json.obj()), tail, value))
      case Nil => base
    }
}

class LinagoraServicesDiscoveryModule() extends AbstractModule {
  override def configure(): Unit = {
    val routes = Multibinder.newSetBinder(binder, classOf[JMAPRoutes])
    routes.addBinding().to(classOf[LinagoraServicesDiscoveryRoutes])
  }

  @Provides
  @Singleton
  def linagoraServicesDiscoveryConfiguration(propertiesProvider: PropertiesProvider): LinagoraServicesDiscoveryConfiguration =
    LinagoraServicesDiscoveryConfiguration.from(propertiesProvider.getConfiguration("linagora-ecosystem"))
}

object LinagoraServicesDiscoveryRoutes {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[LinagoraServicesDiscoveryRoutes])
}

class LinagoraServicesDiscoveryRoutes @Inject()(val servicesDiscoveryConfiguration: LinagoraServicesDiscoveryConfiguration,
                                                @Named(InjectionKeys.RFC_8621) val authenticator: Authenticator) extends JMAPRoutes {
  private val ENDPOINT: String = ".well-known/linagora-ecosystem"

  override def routes(): Stream[JMAPRoute] =
    Stream.of(
      JMAPRoute.builder()
        .endpoint(new Endpoint(HttpMethod.GET, s"/$ENDPOINT"))
        .action((request, response) => generateServicesDiscoveryResponse(request, response))
        .corsHeaders(),
      JMAPRoute.builder()
        .endpoint(new Endpoint(HttpMethod.OPTIONS, s"/$ENDPOINT"))
        .action(JMAPRoutes.CORS_CONTROL)
        .noCorsHeaders())

  private def generateServicesDiscoveryResponse(request: HttpServerRequest, response: HttpServerResponse): Mono[Void] =
    Mono.from(authenticator.authenticate(request))
      .flatMap(_ => response
        .status(HttpResponseStatus.OK)
        .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
        .sendString(Mono.fromCallable(() => ServicesDiscoveryConfigurationSerializers.serialize(servicesDiscoveryConfiguration)))
        .`then`())
      .cast(classOf[Void])
      .onErrorResume {
        case e: UnauthorizedException =>
          LOGGER.warn("Unauthorized", e)
          respondDetails(e.addHeaders(response),
            ProblemDetails(status = UNAUTHORIZED, detail = e.getMessage), UNAUTHORIZED)
        case e =>
          LOGGER.error("Unexpected error upon service discovering", e)
          respondDetails(response,
            ProblemDetails(status = INTERNAL_SERVER_ERROR, detail = e.getMessage), INTERNAL_SERVER_ERROR)
      }

  private def respondDetails(httpServerResponse: HttpServerResponse, problemDetails: ProblemDetails, statusCode: HttpResponseStatus): Mono[Void] =
    Mono.fromCallable(() => ResponseSerializer.serialize(problemDetails))
      .map(Json.stringify)
      .flatMap(problemResponse =>
        Mono.from(httpServerResponse.status(statusCode)
          .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
          .sendString(Mono.just(problemResponse))
          .`then`))
}
