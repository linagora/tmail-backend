package com.linagora.tmail.james.jmap.method

import com.google.inject.{AbstractModule, Scopes}
import com.google.inject.multibindings.{Multibinder, ProvidesIntoSet}
import com.linagora.tmail.james.jmap.json.LongLivedTokenSerializer
import com.linagora.tmail.james.jmap.longlivedtoken.{InMemoryLongLivedTokenStore, LongLivedToken, LongLivedTokenSecret, LongLivedTokenStore}
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.{LINAGORA_LONG_LIVED_TOKEN, LINAGORA_PGP}
import com.linagora.tmail.james.jmap.model.{LongLivedTokenSetRequest, LongLivedTokenSetResponse, TokenCreateRequest, TokenCreateResponse}
import eu.timepit.refined.auto._
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.{Capability, CapabilityProperties, Invocation}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsObject, JsSuccess, Json}
import reactor.core.scala.publisher.SMono

import javax.inject.Inject

case object LongLivedTokenCapabilityProperties extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

case object LongLivedTokenCapability extends Capability {
  val properties: CapabilityProperties = LongLivedTokenCapabilityProperties
  val identifier: CapabilityIdentifier = LINAGORA_LONG_LIVED_TOKEN
}

class LongLivedTokenSetMethodMethodModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[LongLivedTokenSetMethod])

    bind(classOf[InMemoryLongLivedTokenStore]).in(Scopes.SINGLETON)
    bind(classOf[LongLivedTokenStore]).to(classOf[InMemoryLongLivedTokenStore])
  }

  @ProvidesIntoSet
  private def capability(): Capability = LongLivedTokenCapability
}

class LongLivedTokenSetMethod @Inject()(longLivedTokenStore: LongLivedTokenStore,
                                        val metricFactory: MetricFactory,
                                        val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[LongLivedTokenSetRequest] {

  override val methodName: Invocation.MethodName = MethodName("LongLivedToken/set")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_LONG_LIVED_TOKEN)

  override def doProcess(capabilities: Set[CapabilityIdentifier],
                         invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: LongLivedTokenSetRequest): Publisher[InvocationWithContext] =
    createToken(mailboxSession, request.create)
      .map(response => Invocation(
        methodName = methodName,
        arguments = Arguments(LongLivedTokenSerializer.serializeKeystoreGetResponse(
          LongLivedTokenSetResponse(request.accountId, response)).as[JsObject]),
        methodCallId = invocation.invocation.methodCallId))
      .map(InvocationWithContext(_, invocation.processingContext))

  private def createToken(mailboxSession: MailboxSession, tokenCreateRequest: TokenCreateRequest): SMono[TokenCreateResponse] =
    SMono.fromCallable(() => LongLivedTokenSecret.generate)
      .flatMap(secretKey => SMono.fromPublisher(longLivedTokenStore.store(mailboxSession.getUser, LongLivedToken(tokenCreateRequest.deviceId, secretKey)))
        .map(longLivedTokenId => TokenCreateResponse(longLivedTokenId, secretKey)))

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, LongLivedTokenSetRequest] = 
    LongLivedTokenSerializer.deserializeLongLivedTokenSetRequest(invocation.arguments.value) match {
      case JsSuccess(setRequest, _) => setRequest.validate
      case errors: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }
}
