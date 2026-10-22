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
import com.linagora.tmail.jmap.aibot.agent.tools.EmailNotFoundException
import io.netty.handler.codec.http.HttpHeaderNames.{CONTENT_LENGTH, CONTENT_TYPE}
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpResponseStatus.{INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED, BAD_REQUEST, NOT_FOUND, NOT_IMPLEMENTED}
import jakarta.inject.{Inject, Named}
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.core.ProblemDetails
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.Authenticator
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsValue, Json}
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
      .endpoint(new Endpoint(HttpMethod.POST, aiToolsUri))
      .action(this.execute)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, aiToolsUri))
      .action(JMAPRoutes.CORS_CONTROL)
      .noCorsHeaders)

  private def readToolCall(request: HttpServerRequest): SMono[ToolCall] =
    SMono.fromPublisher(request.receive().aggregate().asByteArray())
      .switchIfEmpty(SMono.error(InvalidToolCallException("Empty request body")))
      .flatMap(bytes => SMono.fromCallable(() => Json.parse(bytes))
        .onErrorMap(_ => InvalidToolCallException("Request body is not valid JSON")))
      .flatMap(json => ToolCall.parse(json) match {
        case Right(call) => SMono.just(call)
        case Left(error) => SMono.error(InvalidToolCallException(error))
      })

  private def execute(request: HttpServerRequest, response: HttpServerResponse): Mono[Void] =
    withEligibleSession(request, response) { session =>
      readToolCall(request)
        .flatMap(call => toolRegistry.execute(session, call))
        .flatMap(result => sendResponse(response, OK, result.asJson))
    }

  private def list(request: HttpServerRequest, response: HttpServerResponse): Mono[Void] =
    withEligibleSession(request, response){_=>
      sendResponse(response, OK, Json.obj("tools" -> toolRegistry.definitions()))
    }

  private def sendResponse(response: HttpServerResponse, status: HttpResponseStatus,  json: JsValue): SMono[Unit] = {
    val body: Array[Byte] = Json.stringify(json).getBytes(StandardCharsets.UTF_8)
    SMono.fromPublisher(response.status(status)
      .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
      .header(CONTENT_LENGTH, Integer.toString(body.length))
      .sendByteArray(SMono.just(body))
      .`then`())
      .`then`()
  }

private def withEligibleSession(request: HttpServerRequest, response: HttpServerResponse)
                               (action: MailboxSession => SMono[Unit]): Mono[Void] =
  SMono(authenticator.authenticate(request))
    .flatMap(session =>
      SMono(applyWhenFilter.isEligible(session.getUser))
        .map(_.booleanValue())
        .flatMap {
          case true => action(session)
          case false => respondDetails(response, ProblemDetails(status = UNAUTHORIZED, detail = "User is not eligible for AI tools"))
        })
    .onErrorResume(e => handleError(request, response, e))
    .asJava()
    .`then`


  private def handleError(request: HttpServerRequest, response: HttpServerResponse, error: Throwable): SMono[Unit] =
    error match {
      case e: UnauthorizedException =>
        LOGGER.warn("Unauthorized", e)
        respondDetails(e.addHeaders(response), ProblemDetails(status = UNAUTHORIZED, detail = e.getMessage))
      case e: IllegalArgumentException =>
        respondDetails(response, ProblemDetails(status = BAD_REQUEST, detail = e.getMessage))
      case e: ToolNotFoundException =>
        respondDetails(response, ProblemDetails(status = NOT_FOUND, detail = e.getMessage))
      case e: EmailNotFoundException =>
        respondDetails(response, ProblemDetails(status = NOT_FOUND, detail = e.getMessage))
      case _: MailboxNotFoundException =>
        respondDetails(response, ProblemDetails(status = NOT_FOUND, detail = "Mailbox not found"))
      case e: UnsupportedOperationException =>
        respondDetails(response, ProblemDetails(status = NOT_IMPLEMENTED, detail = e.getMessage))
      case e =>
        LOGGER.error("Unexpected error on AI tools route {}", request.uri(), e)
        respondDetails(response, ProblemDetails(status = INTERNAL_SERVER_ERROR, detail = e.getMessage))
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
