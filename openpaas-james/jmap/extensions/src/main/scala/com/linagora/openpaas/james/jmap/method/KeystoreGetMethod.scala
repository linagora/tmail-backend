package com.linagora.openpaas.james.jmap.method

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.openpaas.encrypted.KeystoreManager
import com.linagora.openpaas.james.jmap.json.KeystoreSerializer
import com.linagora.openpaas.james.jmap.method.CapabilityIdentifier.LINAGORA_PGP
import com.linagora.openpaas.james.jmap.model.{KeystoreGetRequest, KeystoreGetResponse}
import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Invocation, State}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.SFlux

class KeystoreGetMethodModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[KeystoreGetMethod])
  }
}

class KeystoreGetMethod @Inject()(serializer: KeystoreSerializer,
                                  keystoreManager: KeystoreManager,
                                  val metricFactory: MetricFactory,
                                  val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[KeystoreGetRequest] {

  override val methodName: Invocation.MethodName = MethodName("Keystore/get")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_PGP)

  override def doProcess(capabilities: Set[CapabilityIdentifier],
                         invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: KeystoreGetRequest): Publisher[InvocationWithContext] = {
    SFlux.fromPublisher(keystoreManager.listPublicKeys(mailboxSession.getUser))
      .map(key => (key.id, key))
      .collectSeq()
      .map(seq => KeystoreGetResponse(request.accountId, State.INSTANCE, seq.toMap))
      .map(response => InvocationWithContext(
        invocation = Invocation(
          methodName = methodName,
          arguments = Arguments(serializer.serializeKeystoreGetResponse(KeystoreGetResponse(
            accountId = request.accountId,
            state = response.state,
            list = response.list)).as[JsObject]),
          methodCallId = invocation.invocation.methodCallId), processingContext = invocation.processingContext))
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, KeystoreGetRequest] = {
    serializer.deserializeKeystoreGetRequest(invocation.arguments.value) match {
      case JsSuccess(keystoreGetRequest, _) => Right(keystoreGetRequest)
      case error: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(error).toString))
    }
  }
}
