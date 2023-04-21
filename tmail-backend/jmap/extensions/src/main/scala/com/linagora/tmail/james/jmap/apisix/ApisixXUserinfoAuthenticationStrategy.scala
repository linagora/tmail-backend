package com.linagora.tmail.james.jmap.apisix

import com.fasterxml.jackson.core.JsonParseException
import com.google.common.collect.ImmutableMap
import com.linagora.tmail.james.jmap.apisix.ApisixXUserinfoAuthenticationStrategy.{X_USER_CHALLENGE, X_USER_HEADER_NAME}
import com.linagora.tmail.james.jmap.ticket.ForbiddenException
import org.apache.james.core.Username
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.{AuthenticationChallenge, AuthenticationScheme, AuthenticationStrategy}
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.user.api.UsersRepository
import org.apache.james.util.ReactorUtils
import play.api.libs.json.{JsValue, Json}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.netty.http.server.HttpServerRequest

import java.util.Base64
import javax.inject.Inject

object ApisixXUserinfoAuthenticationStrategy {
  val X_USER_HEADER_NAME: String = "X-Userinfo"
  val X_USER_CHALLENGE: AuthenticationChallenge = AuthenticationChallenge.of(AuthenticationScheme.of("XUserInfoHeader"), ImmutableMap.of)

  def extractUserFromHeader(xUserInfoHeader: String): Option[String] = {
    val userInfoJson: JsValue = Json.parse(new String(Base64.getDecoder.decode(xUserInfoHeader)))
    (userInfoJson \ "sub").asOpt[String]
  }
}

class ApisixXUserinfoAuthenticationStrategy @Inject()(usersRepository: UsersRepository,
                                                      mailboxManager: MailboxManager) extends AuthenticationStrategy {
  override def createMailboxSession(httpRequest: HttpServerRequest): Mono[MailboxSession] =
    SMono.justOrEmpty(httpRequest.requestHeaders().get(X_USER_HEADER_NAME))
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(ApisixXUserinfoAuthenticationStrategy.extractUserFromHeader)
      .flatMap(SMono.justOrEmpty)
      .map(Username.of)
      .flatMap(createMailboxSession)
      .onErrorResume {
        case _: ForbiddenException | _: JsonParseException => SMono.error(new UnauthorizedException("User is forbidden"))
      }
      .asJava()

  override def correspondingChallenge(): AuthenticationChallenge = X_USER_CHALLENGE

  private def createMailboxSession(username: Username): SMono[MailboxSession] =
    SMono.fromCallable(() => {
      usersRepository.assertValid(username)
      mailboxManager.authenticate(username).withoutDelegation()
    }).subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
}
