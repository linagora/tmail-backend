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

package com.linagora.tmail.jmap.aibot.agent

import com.linagora.tmail.james.jmap.event.ApplyWhenFilter
import com.linagora.tmail.jmap.aibot.agent.AIToolsRoutes.LOGGER
import io.netty.handler.codec.http.HttpHeaderNames.{CONTENT_LENGTH, CONTENT_TYPE}
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus.{INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED}
import jakarta.inject.{Inject, Named}
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.core.ProblemDetails
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.Authenticator
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsObject, Json}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

import java.nio.charset.StandardCharsets
import java.util.stream
import java.util.stream.Stream

object AIToolsRoutes {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[AIToolsRoutes])
}

class AIToolsRoutes @Inject()(@Named(InjectionKeys.RFC_8621) val authenticator: Authenticator,
                              val toolRegistry: AgentToolRegistry,
                              val applyWhenFilter: ApplyWhenFilter) extends JMAPRoutes {
  private val aiToolsUri = "/ai/v1/tools"

  override def routes(): stream.Stream[JMAPRoute] = Stream.of(
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.GET, aiToolsUri))
      .action(this.list)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, aiToolsUri))
      .action(JMAPRoutes.CORS_CONTROL)
      .noCorsHeaders)

  private def list(request: HttpServerRequest, response: HttpServerResponse): Mono[Void] =
    SMono(authenticator.authenticate(request))
      .flatMap(mailbox =>
        SMono(applyWhenFilter.isEligible(mailbox.getUser))
          .map(_.booleanValue())
          .flatMap {
            case true => sendResponse(response, toolRegistry.definitions()).`then`()
            case false =>
              respondDetails(response, ProblemDetails(status = UNAUTHORIZED, detail = "User is not eligible for AI tools"))
          })
      .onErrorResume {
        case e: UnauthorizedException =>
          LOGGER.warn("Unauthorized", e)
          respondDetails(e.addHeaders(response), ProblemDetails(status = UNAUTHORIZED, detail = e.getMessage))
        case e =>
          LOGGER.error("Unexpected error upon listing AI tools {}", request.uri(), e)
          respondDetails(response, ProblemDetails(status = INTERNAL_SERVER_ERROR, detail = e.getMessage))
      }
      .asJava()
      .`then`

  private def sendResponse(response: HttpServerResponse, toolDefinitions: Seq[JsObject]): SMono[Void] = {
    val body: Array[Byte] = Json.stringify(Json.obj("tools" -> toolDefinitions)).getBytes(StandardCharsets.UTF_8)
    SMono.fromPublisher(response.status(OK)
      .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
      .header(CONTENT_LENGTH, Integer.toString(body.length))
      .sendByteArray(SMono.just(body))
      .`then`())
  }

  private def respondDetails(httpServerResponse: HttpServerResponse, details: ProblemDetails): SMono[Unit] =
    SMono.fromCallable(() => ResponseSerializer.serialize(details))
      .map(Json.stringify)
      .map(_.getBytes(StandardCharsets.UTF_8))
      .flatMap(bytes =>
        SMono.fromPublisher(httpServerResponse.status(details.status)
          .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
          .header(CONTENT_LENGTH, Integer.toString(bytes.length))
          .sendByteArray(SMono.just(bytes))
          .`then`)
        .`then`)
}
