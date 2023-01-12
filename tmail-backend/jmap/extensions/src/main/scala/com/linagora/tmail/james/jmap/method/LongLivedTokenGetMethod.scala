package com.linagora.tmail.james.jmap.method

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.json.LongLivedTokenSerializer
import com.linagora.tmail.james.jmap.longlivedtoken.{LongLivedTokenFootPrint, LongLivedTokenStore}
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_LONG_LIVED_TOKEN
import com.linagora.tmail.james.jmap.model.{LongLivedTokenGetRequest, LongLivedTokenGetResponse}
import eu.timepit.refined.auto._

import javax.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Invocation, SessionTranslator, UuidState}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.SFlux

class LongLivedTokenGetMethodModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[LongLivedTokenGetMethod])
  }
}

class LongLivedTokenGetMethod @Inject()(longLivedTokenStore: LongLivedTokenStore,
                                        val metricFactory: MetricFactory,
                                        val sessionTranslator: SessionTranslator,
                                        val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[LongLivedTokenGetRequest] {

  override val methodName: Invocation.MethodName = MethodName("LongLivedToken/get")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_LONG_LIVED_TOKEN)

  override def doProcess(capabilities: Set[CapabilityIdentifier],
                         invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: LongLivedTokenGetRequest): Publisher[InvocationWithContext] = {
    SFlux.fromPublisher[LongLivedTokenFootPrint](request.ids match {
      case None => longLivedTokenStore.listTokens(mailboxSession.getUser)
      case Some(ids) => SFlux.fromPublisher[LongLivedTokenFootPrint](longLivedTokenStore.listTokens(mailboxSession.getUser))
        .filter(footPrint => ids.contains(footPrint.id))
    })
      .collectSeq()
      .map(seq => LongLivedTokenGetResponse(
        accountId = request.accountId,
        state = UuidState.INSTANCE,
        list = seq.toList,
        notFound = request.notFound(seq.map(_.id).toSet)))
      .map(response => InvocationWithContext(
        invocation = Invocation(
          methodName = methodName,
          arguments = Arguments(LongLivedTokenSerializer.serializeLongLivedTokenGetResponse(response).as[JsObject]),
          methodCallId = invocation.invocation.methodCallId), processingContext = invocation.processingContext))
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, LongLivedTokenGetRequest] = {
    LongLivedTokenSerializer.deserializeLongLivedTokenGetRequest(invocation.arguments.value) match {
      case JsSuccess(getRequest, _) => Right(getRequest)
      case error: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(error).toString))
    }
  }
}
