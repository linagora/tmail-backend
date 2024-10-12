package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.{SupportEmailSerializer => Serializer}
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_CONTACT_SUPPORT
import com.linagora.tmail.james.jmap.model.{SupportEmailGetRequest, SupportEmailGetResponse}
import jakarta.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.method.{InvocationWithContext, MethodWithoutAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.SMono

class SupportEmailGetMethod @Inject()(val metricFactory: MetricFactory, val sessionSupplier: SessionSupplier) extends MethodWithoutAccountId[SupportEmailGetRequest] {
  private object SupportEmailGetMethod {
    val SUPPORT_EMAIL = "hnasri@linagora.com"
  }

  override val methodName: Invocation.MethodName = MethodName("SupportEmail/get")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL, LINAGORA_CONTACT_SUPPORT)

  override def getRequest(invocation: Invocation): Either[Exception, SupportEmailGetRequest] =
    Serializer.deserializeGetRequest(invocation.arguments.value).asEitherRequest

  override def doProcess(invocation: InvocationWithContext, mailboxSession: MailboxSession, request: SupportEmailGetRequest): Publisher[InvocationWithContext] =
    SMono.just(SupportEmailGetMethod.SUPPORT_EMAIL)
      .map(SupportEmailGetResponse)
      .map(response => Invocation(
        methodName = methodName,
        arguments = Arguments(Serializer.serializeGetResponse(response).as[JsObject]),
        methodCallId = invocation.invocation.methodCallId))
      .map(InvocationWithContext(_, invocation.processingContext))
}
