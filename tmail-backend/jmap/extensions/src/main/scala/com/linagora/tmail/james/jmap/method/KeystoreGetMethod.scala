package com.linagora.tmail.james.jmap.method

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.encrypted.{KeystoreManager, PublicKey}
import com.linagora.tmail.james.jmap.json.KeystoreSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_PGP
import com.linagora.tmail.james.jmap.model.{KeystoreGetRequest, KeystoreGetResponse}
import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Invocation, SessionTranslator, UuidState}
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

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
                                  val sessionTranslator: SessionTranslator,
                                  val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[KeystoreGetRequest] {

  override val methodName: Invocation.MethodName = MethodName("Keystore/get")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_PGP)

  override def doProcess(capabilities: Set[CapabilityIdentifier],
                         invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: KeystoreGetRequest): Publisher[InvocationWithContext] = {
    SFlux.fromPublisher[PublicKey](request.ids match {
      case None => keystoreManager.listPublicKeys(mailboxSession.getUser)
      case Some(ids) => SFlux.fromIterable(ids)
        .flatMap(id => SMono.fromPublisher(keystoreManager.retrieveKey(mailboxSession.getUser, id)))
    })
      .collectSeq()
      .map(seq => KeystoreGetResponse(
        accountId = request.accountId,
        state = UuidState.INSTANCE,
        list = seq.toList,
        notFound = request.notFound(seq.map(_.id).toSet)))
      .map(response => InvocationWithContext(
        invocation = Invocation(
          methodName = methodName,
          arguments = Arguments(serializer.serializeKeystoreGetResponse(response).as[JsObject]),
          methodCallId = invocation.invocation.methodCallId), processingContext = invocation.processingContext))
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, KeystoreGetRequest] =
    serializer.deserializeKeystoreGetRequest(invocation.arguments.value).asEitherRequest
}
