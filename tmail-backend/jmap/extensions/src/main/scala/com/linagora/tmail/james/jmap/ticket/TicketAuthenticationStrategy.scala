package com.linagora.tmail.james.jmap.ticket

import cats.implicits._
import io.netty.handler.codec.http.QueryStringDecoder

import javax.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.JmapRfc8621Configuration
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.{AuthenticationChallenge, AuthenticationScheme, AuthenticationStrategy}
import org.apache.james.jmap.routes.JmapUrlEndpointResolver
import org.apache.james.mailbox.{MailboxSession, SessionProvider}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.netty.http.server.HttpServerRequest

import scala.jdk.CollectionConverters._

class TicketAuthenticationStrategy @Inject() (ticketManager: TicketManager,
                                              sessionProvider: SessionProvider,
                                              configuration: JmapRfc8621Configuration) extends AuthenticationStrategy {

  override def createMailboxSession(httpRequest: HttpServerRequest): Mono[MailboxSession] =
    retrieveTicket(httpRequest)
      .fold(_ => SMono.error(new UnauthorizedException("Invalid ticket passed as a query parameter")),
        maybeTicket => maybeTicket
          .map(ticketValue => ticketManager.validate(ticketValue, httpRequest.remoteAddress().getAddress))
          .getOrElse(SMono.empty[Username]))
      .map(sessionProvider.createSystemSession)
      .onErrorResume {
        case _: ForbiddenException => SMono.error(new UnauthorizedException("User is forbidden to use this ticket"))
      }
      .asJava()


  override def correspondingChallenge(): AuthenticationChallenge = AuthenticationChallenge.of(
    AuthenticationScheme.of("Ticket"),
    Map("realm" -> configuration.urlPrefixString).asJava)

  private def retrieveTicket(httpRequest: HttpServerRequest): Either[IllegalArgumentException, Option[TicketValue]] =
    queryParam(httpRequest, "ticket")
      .map(TicketValue.parse)
      .sequence

  private def queryParam(httpRequest: HttpServerRequest, parameterName: String): Option[String] = queryParam(parameterName, httpRequest.uri)

  private def queryParam(parameterName: String, uri: String): Option[String] = Option(new QueryStringDecoder(uri).parameters.get(parameterName))
    .map(_.asScala)
    .flatMap(_.headOption)
}
