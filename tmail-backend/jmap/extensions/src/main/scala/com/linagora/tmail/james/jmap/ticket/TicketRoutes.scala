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

package com.linagora.tmail.james.jmap.ticket

import java.net.{URI, URL}
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.util.stream

import com.google.inject.multibindings.{Multibinder, ProvidesIntoSet}
import com.google.inject.{AbstractModule, Provides, Scopes, Singleton}
import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration
import com.linagora.tmail.james.jmap.json.TicketSerializer
import com.linagora.tmail.james.jmap.ticket.TicketRoutes.{ENDPOINT, LOGGER, TICKET_PARAM}
import com.linagora.tmail.james.jmap.ticket.TicketRoutesCapability.LINAGORA_WS_TICKET
import eu.timepit.refined.auto._
import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.HttpResponseStatus.{BAD_REQUEST, FORBIDDEN, INTERNAL_SERVER_ERROR, NO_CONTENT, UNAUTHORIZED}
import io.netty.handler.codec.http.{HttpMethod, HttpResponseStatus}
import jakarta.inject.{Inject, Named}
import org.apache.james.core.Username
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, JmapRfc8621Configuration, ProblemDetails, UrlPrefixes}
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.http.{AuthenticationStrategy, Authenticator}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.routes.ForbiddenException
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.apache.james.mailbox.SessionProvider
import org.apache.james.util.ReactorUtils
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsObject, Json}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

case class TicketRoutesModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[MemoryTicketStore]).in(Scopes.SINGLETON)
    bind(classOf[TicketStore]).to(classOf[MemoryTicketStore])

    val routes = Multibinder.newSetBinder(binder, classOf[JMAPRoutes])
    routes.addBinding.to(classOf[TicketRoutes])

    val authenticationStrategies = Multibinder.newSetBinder(binder, classOf[AuthenticationStrategy])
    authenticationStrategies.addBinding.to(classOf[TicketAuthenticationStrategy])
  }

  @ProvidesIntoSet
  private def capability(): CapabilityFactory = TicketRoutesCapabilityFactory

  @Provides
  @Singleton
  def provideTicketManager(clock: Clock, ticketStore: TicketStore,
                           jmapExtensionConfiguration: JMAPExtensionConfiguration): TicketManager =
    new TicketManager(clock, ticketStore, jmapExtensionConfiguration.ticketIpValidationEnable.value)

  @Provides
  @Singleton
  def provideTicketAuthenticationStrategy(ticketManager: TicketManager, sessionProvider: SessionProvider,
                                          configuration: JmapRfc8621Configuration): TicketAuthenticationStrategy =
    new TicketAuthenticationStrategy(ticketManager, sessionProvider, configuration.urlPrefixString)
}

object TicketRoutesCapability {
  val LINAGORA_WS_TICKET: CapabilityIdentifier = "com:linagora:params:jmap:ws:ticket"
}

case object TicketRoutesCapabilityFactory extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes, username: Username): Capability = TicketRoutesCapability(TicketRoutesCapabilityProperties(urlPrefixes.httpUrlPrefix.toString))

  override def id(): CapabilityIdentifier = LINAGORA_WS_TICKET
}

case class TicketRoutesCapability(properties: TicketRoutesCapabilityProperties) extends Capability {
  val identifier: CapabilityIdentifier = LINAGORA_WS_TICKET
}

case class TicketRoutesCapabilityProperties(urlPrefix: String) extends CapabilityProperties {
  val generationEndpoint: URL = new URI(s"$urlPrefix/$ENDPOINT").toURL
  val revocationEndpoint: URL = new URI(s"$urlPrefix/$ENDPOINT").toURL

  override def jsonify(): JsObject = Json.obj(
    ("generationEndpoint", generationEndpoint.toString),
    ("revocationEndpoint", revocationEndpoint.toString))
}

object TicketRoutes {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[TicketRoutes])
  val ENDPOINT: String = "jmap/ws/ticket"
  val TICKET_PARAM: String = "ticket"
  val REVOCATION_ENDPOINT = s"$ENDPOINT/{$TICKET_PARAM}"
}

class TicketRoutes @Inject() (@Named(InjectionKeys.RFC_8621) val authenticator: Authenticator, ticketManager: TicketManager) extends JMAPRoutes {
  override def routes(): stream.Stream[JMAPRoute] = stream.Stream.of(
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.POST, s"/${baseEndpoint()}"))
      .action(this.generate)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, s"/${baseEndpoint()}"))
      .action(JMAPRoutes.CORS_CONTROL)
      .corsHeaders(),
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.DELETE, s"/${baseEndpoint()}/{$TICKET_PARAM}"))
      .action(this.revoke)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, s"/${baseEndpoint()}/{$TICKET_PARAM}"))
      .action(JMAPRoutes.CORS_CONTROL)
      .corsHeaders())

  protected def baseEndpoint(): String = ENDPOINT

  private def generate(request: HttpServerRequest, response: HttpServerResponse): Mono[Void] =
    SMono(authenticator.authenticate(request))
      .flatMap(session => ticketManager.generate(session.getUser, request.remoteAddress().getAddress))
      .map(TicketSerializer.serialize)
      .map(Json.stringify)
      .flatMap(ticket => SMono(response
        .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
        .sendString(SMono.just(ticket))
        .`then`()))
      .onErrorResume(e => handleException(e, response))
      .asJava()
      .`then`()

  private def revoke(request: HttpServerRequest, response: HttpServerResponse): Mono[Void] =
    SMono(authenticator.authenticate(request))
      .flatMap(session => {
        val value = TicketValue.parse(request.param(TICKET_PARAM))
        value.fold(e => SMono.error(e), ticketValue => ticketManager.revoke(ticketValue, session.getUser))
      })
      .`then`(SMono(response.status(NO_CONTENT).send()))
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
      .onErrorResume(e => handleException(e, response))
      .asJava()
      .`then`()

  private def handleException(throwable: Throwable, response: HttpServerResponse): SMono[Unit] = throwable match {
    case e: UnauthorizedException =>
      LOGGER.warn("Unauthorized", e)
      respondDetails(response,
        ProblemDetails(status = UNAUTHORIZED, detail = e.getMessage),
        UNAUTHORIZED)
    case e: IllegalArgumentException =>
      respondDetails(response,
        ProblemDetails(status = BAD_REQUEST, detail = s"Invalid request: ${e.getMessage}"),
        FORBIDDEN)
    case _: ForbiddenException =>
      respondDetails(response,
        ProblemDetails(status = FORBIDDEN, detail = "Using ticket other accounts is forbidden"),
        FORBIDDEN)
    case e =>
      LOGGER.error("Unexpected error upon uploads", e)
      respondDetails(response,
        ProblemDetails(status = INTERNAL_SERVER_ERROR, detail = e.getMessage),
        INTERNAL_SERVER_ERROR)
  }

  private def respondDetails(httpServerResponse: HttpServerResponse, details: ProblemDetails, statusCode: HttpResponseStatus = BAD_REQUEST): SMono[Unit] =
    SMono.fromPublisher(httpServerResponse.status(statusCode)
      .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
      .sendString(SMono.fromCallable(() => ResponseSerializer.serialize(details).toString), StandardCharsets.UTF_8)
      .`then`).`then`
}
