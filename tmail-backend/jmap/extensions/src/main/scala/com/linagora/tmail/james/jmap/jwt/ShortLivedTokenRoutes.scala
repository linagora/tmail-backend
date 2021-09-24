package com.linagora.tmail.james.jmap.jwt

import com.google.inject.multibindings.Multibinder
import com.google.inject.name.Named
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.linagora.tmail.james.jmap.LongLivedTokenInjectKeys
import com.linagora.tmail.james.jmap.json.JwtTokenSerializer
import com.linagora.tmail.james.jmap.jwt.ShortLivedTokenRoutes.{DEVICE_ID_PARAM, ENDPOINT, TOKEN_DURATION_DEFAULT, TYPE_ACCEPT_PARAM, TYPE_PARAM}
import com.linagora.tmail.james.jmap.longlivedtoken.{AuthenticationToken, DeviceId, LongLivedTokenAuthenticationStrategy, LongLivedTokenSecret, LongLivedTokenStore}
import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.HttpResponseStatus.{BAD_REQUEST, FORBIDDEN, INTERNAL_SERVER_ERROR, UNAUTHORIZED}
import io.netty.handler.codec.http.{HttpMethod, HttpResponseStatus, QueryStringDecoder}
import org.apache.james.core.Username
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.core.ProblemDetails
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.{AuthenticationStrategy, Authenticator, BasicAuthenticationStrategy}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.routes.ForbiddenException
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.Json
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

import java.nio.charset.StandardCharsets
import java.time.{Clock, Duration, ZonedDateTime}
import java.util.stream
import javax.inject.Inject
import scala.jdk.CollectionConverters._

case class ShortLivedTokenRoutesModule() extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder, classOf[JMAPRoutes])
      .addBinding().to(classOf[ShortLivedTokenRoutes])
  }

  @Provides
  @Singleton
  @Named(LongLivedTokenInjectKeys.JMAP)
  def provideLongLivedTokenAuthenticator(metricFactory: MetricFactory,
                                         longLivedTokenAuthenticationStrategy: LongLivedTokenAuthenticationStrategy,
                                         basicAuthenticationStrategy: BasicAuthenticationStrategy): Authenticator =
    Authenticator.of(metricFactory, longLivedTokenAuthenticationStrategy, basicAuthenticationStrategy)

}

case class JwtTokenResponse(token: JWTToken, expiresOn: ZonedDateTime)

object ShortLivedTokenRoutes {
  val ENDPOINT: String = "token"
  val TOKEN_DURATION_DEFAULT: Duration = Duration.ofHours(1)
  val TYPE_PARAM: String = "type"
  val DEVICE_ID_PARAM: String = "deviceId"
  val TYPE_ACCEPT_PARAM: String = "shortLived"
}

class ShortLivedTokenRoutes @Inject()(@Named(LongLivedTokenInjectKeys.JMAP) val authenticator: Authenticator,
                                      longLivedTokenStore: LongLivedTokenStore,
                                      jwtSigner: JwtSigner,
                                      clock: Clock) extends JMAPRoutes {
  override def routes(): stream.Stream[JMAPRoute] = stream.Stream.of(
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.GET, s"/$ENDPOINT"))
      .action(this.generate)
      .corsHeaders())

  private def generate(request: HttpServerRequest, response: HttpServerResponse): SMono[Unit] =
    SMono(authenticator.authenticate(request))
      .flatMap(session => validRequest(session.getUser, request)
        .`then`(SMono.just(session.getUser)))
      .map(user => {
        val validUntil: ZonedDateTime = ZonedDateTime.ofInstant(clock.instant().plusSeconds(TOKEN_DURATION_DEFAULT.toSeconds), clock.getZone)
        JwtTokenResponse(jwtSigner.sign(user, validUntil), validUntil)
      })
      .map(JwtTokenSerializer.serializeJwtTokenResponse)
      .map(Json.stringify)
      .flatMap(token => SMono(response
        .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
        .sendString(SMono.just(token))))
      .onErrorResume(error => handleException(error, response))
      .subscribeOn(Schedulers.elastic())
      .`then`()

  private def validRequest(username: Username, request: HttpServerRequest): SMono[Unit] =
    SMono.just(isAuthenByLongLivedToken(request))
      .doOnNext(isAuthenByLongLivedToken => validRequestParameter(request, isAuthenByLongLivedToken))
      .filter(isAuthenByLongLivedToken => isAuthenByLongLivedToken)
      .flatMap(_ => SMono.justOrEmpty(extractLongLivedTokenSecretAndDeviceId(request))
        .switchIfEmpty(SMono.error(new UnauthorizedException("'deviceId' is not valid")))
        .flatMap(secretAndDevice => validateToken(username, secretAndDevice._2, secretAndDevice._1)))

  private def extractLongLivedTokenSecretAndDeviceId(request: HttpServerRequest): Option[(LongLivedTokenSecret, DeviceId)] =
    Option(request.requestHeaders.get(AuthenticationStrategy.AUTHORIZATION_HEADERS))
      .flatMap(authHeader => AuthenticationToken.fromAuthHeader(authHeader))
      .map(authenticationToken => authenticationToken.secret)
      .flatMap(secret => queryParam(DEVICE_ID_PARAM, request.uri())
        .map(deviceId => secret -> DeviceId(deviceId)))

  private def validateToken(username: Username,
                            deviceId: DeviceId,
                            secret: LongLivedTokenSecret): SMono[Unit] =
    SMono.fromPublisher(longLivedTokenStore.validate(username, secret))
      .filter(tokenFootPrint => tokenFootPrint.deviceId.equals(deviceId))
      .switchIfEmpty(SMono.error(new UnauthorizedException("'deviceId' is not valid")))
      .`then`()

  private def validRequestParameter(request: HttpServerRequest, isAuthenByLongLivedToken: Boolean): Unit = {
    if (!queryParam(TYPE_PARAM, request.uri()).exists(typeValue => TYPE_ACCEPT_PARAM.equals(typeValue))) {
      throw new IllegalArgumentException(s"'$TYPE_PARAM' must be $TYPE_ACCEPT_PARAM")
    }
    if (isAuthenByLongLivedToken) {
      if (queryParam(DEVICE_ID_PARAM, request.uri()).isEmpty) {
        throw new IllegalArgumentException(s"'$DEVICE_ID_PARAM' must be not empty")
      }
    }
  }

  private def isAuthenByLongLivedToken(request: HttpServerRequest): Boolean =
    Option(request.requestHeaders().get(AuthenticationStrategy.AUTHORIZATION_HEADERS))
      .exists(authenValue => authenValue.startsWith(LongLivedTokenAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX))

  private def queryParam(parameterName: String, uri: String): Option[String] =
    Option(new QueryStringDecoder(uri).parameters.get(parameterName))
      .toList
      .flatMap(_.asScala)
      .headOption

  private def handleException(throwable: Throwable, response: HttpServerResponse): SMono[Unit] = throwable match {
    case e: UnauthorizedException =>
      respondDetails(response,
        ProblemDetails(status = UNAUTHORIZED, detail = e.getMessage),
        UNAUTHORIZED)
    case e: IllegalArgumentException =>
      respondDetails(response,
        ProblemDetails(status = BAD_REQUEST, detail = s"Invalid request: ${e.getMessage}"),
        BAD_REQUEST)
    case _: ForbiddenException =>
      respondDetails(response,
        ProblemDetails(status = FORBIDDEN, detail = "Using token other accounts is forbidden"),
        FORBIDDEN)
    case e =>
      respondDetails(response,
        ProblemDetails(status = INTERNAL_SERVER_ERROR, detail = e.getMessage),
        INTERNAL_SERVER_ERROR)
  }

  private def respondDetails(httpServerResponse: HttpServerResponse, details: ProblemDetails, statusCode: HttpResponseStatus = BAD_REQUEST): SMono[Unit] =
    SMono.fromPublisher(httpServerResponse.status(statusCode)
      .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
      .sendString(SMono.fromCallable(() => ResponseSerializer.serialize(details).toString), StandardCharsets.UTF_8)
      .`then`)
      .`then`
}
