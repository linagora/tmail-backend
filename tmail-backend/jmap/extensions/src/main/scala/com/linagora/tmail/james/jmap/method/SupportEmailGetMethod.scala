package com.linagora.tmail.james.jmap.method

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.json.{SupportEmailSerializer => Serializer}
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_CONTACT_SUPPORT
import com.linagora.tmail.james.jmap.model.{SupportEmailGetRequest, SupportEmailGetResponse}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.ErrorCode.Forbidden
import org.apache.james.jmap.core.Invocation
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodWithoutAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.SMono

class SupportEmailGetMethodModule extends AbstractModule {
  override def configure(): Unit = {

  }
}

class SupportEmailGetMethod @Inject()(val metricFactory: MetricFactory, val sessionSupplier: SessionSupplier) extends MethodWithoutAccountId[SupportEmailGetRequest] {
  private object SupportEmailGetMethod {
    val SUPPORT_EMAIL = "hnasri@linagora.com"
  }

  override val methodName: MethodName = MethodName("SupportEmail/get")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_CONTACT_SUPPORT)

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
      .onErrorResume(throw new RuntimeException("Baaad!!"))
}
