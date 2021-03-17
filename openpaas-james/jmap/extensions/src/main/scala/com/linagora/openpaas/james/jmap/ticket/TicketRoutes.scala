package com.linagora.openpaas.james.jmap.ticket

import java.nio.charset.StandardCharsets
import java.util.stream

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.openpaas.james.jmap.json.TicketSerializer
import com.linagora.openpaas.james.jmap.ticket.TicketRoutes.{ENDPOINT, REVOCATION_ENDPOINT, TICKET_PARAM}
import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.HttpResponseStatus.{BAD_REQUEST, FORBIDDEN, INTERNAL_SERVER_ERROR, NO_CONTENT, UNAUTHORIZED}
import io.netty.handler.codec.http.{HttpMethod, HttpResponseStatus}
import javax.inject.{Inject, Named}
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.core.ProblemDetails
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.http.{AuthenticationStrategy, Authenticator}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.routes.ForbiddenException
import org.apache.james.jmap.routes.UploadRoutes.LOGGER
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import play.api.libs.json.Json
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
}

object TicketRoutes {
  val ENDPOINT: String = "/jmap/ws/ticket"
  val TICKET_PARAM: String = "ticket"
  val REVOCATION_ENDPOINT = s"$ENDPOINT/{$TICKET_PARAM}"
}

class TicketRoutes @Inject() (@Named(InjectionKeys.RFC_8621) val authenticator: Authenticator, ticketManager: TicketManager) extends JMAPRoutes {
  override def routes(): stream.Stream[JMAPRoute] = stream.Stream.of(
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.POST, ENDPOINT))
      .action(this.generate)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, ENDPOINT))
      .action(JMAPRoutes.CORS_CONTROL)
      .corsHeaders(),
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.DELETE, REVOCATION_ENDPOINT))
      .action(this.revoke)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, REVOCATION_ENDPOINT))
      .action(JMAPRoutes.CORS_CONTROL)
      .corsHeaders())

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
