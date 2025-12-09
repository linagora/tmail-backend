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

package com.linagora.tmail.jmap.aibot

import java.nio.charset.StandardCharsets
import java.util.stream
import java.util.stream.Stream

import com.linagora.tmail.jmap.aibot.AIChatCompletionRoutes.LOGGER
import com.linagora.tmail.mailet.rag.RagConfig
import com.linagora.tmail.mailet.rag.httpclient.OpenRagHttpClient
import io.netty.handler.codec.http.HttpHeaderNames.{CONTENT_LENGTH, CONTENT_TYPE}
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus.{INTERNAL_SERVER_ERROR, UNAUTHORIZED}
import jakarta.inject.{Inject, Named}
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.core.ProblemDetails
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.Authenticator
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

object AIChatCompletionRoutes {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[AIChatCompletionRoutes])
}

class AIChatCompletionRoutes @Inject()(@Named(InjectionKeys.RFC_8621) val authenticator: Authenticator,
                                       val ragConfig: RagConfig) extends JMAPRoutes {
  private val openRagHttpClient = new OpenRagHttpClient(ragConfig)
  private val jmapAiUri = "/ai/v1/chat/completions"

  override def routes(): stream.Stream[JMAPRoute] = Stream.of(
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.POST, jmapAiUri))
      .action(this.post)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, jmapAiUri))
      .action(JMAPRoutes.CORS_CONTROL)
      .noCorsHeaders)

  private def post(request: HttpServerRequest, response: HttpServerResponse): Mono[Void] =
    SMono(authenticator.authenticate(request))
      .flatMap(_ => postRequest(request, response))
      .onErrorResume {
        case e: UnauthorizedException =>
          LOGGER.warn("Unauthorized", e)
          respondDetails(e.addHeaders(response), ProblemDetails(status = UNAUTHORIZED, detail = e.getMessage))
        case e =>
          LOGGER.error("Unexpected error upon calling LLM chat completion {}", request.uri(), e)
          respondDetails(response, ProblemDetails(status = INTERNAL_SERVER_ERROR, detail = e.getMessage))
      }
      .asJava()
      .`then`

  private def postRequest(request: HttpServerRequest, response: HttpServerResponse): SMono[Unit] =
    SMono.fromPublisher(request
        .receive()
        .aggregate()
        .asByteArray())
      .flatMap(input => SMono.fromPublisher(openRagHttpClient.proxyChatCompletions(input)))
      .flatMap(result => SMono.fromPublisher(response.status(result.status())
            .headers(result.headers())
            .sendByteArray(SMono.just(result.body()))
          .`then`())
        .`then`())

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
