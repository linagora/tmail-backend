package com.linagora.openpaas.james.jmap.method

import com.google.inject.AbstractModule
import com.google.inject.multibindings.{Multibinder, ProvidesIntoSet}
import com.linagora.openpaas.james.jmap.json.KeystoreSerializer
import com.linagora.openpaas.james.jmap.method.CapabilityIdentifier.LINAGORA_PGP
import com.linagora.openpaas.james.jmap.model.{KeystoreSetRequest, KeystoreSetResponse}
import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Capability, CapabilityProperties, ClientId, Id, Invocation, ServerId, SetError}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.{JsError, JsObject, JsSuccess, Json}
import reactor.core.scala.publisher.SMono

case object KeystoreCapabilityProperties extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

case object KeystoreCapability extends Capability {
  val properties: CapabilityProperties = KeystoreCapabilityProperties
  val identifier: CapabilityIdentifier = LINAGORA_PGP
}

class KeystoreCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(): Capability = KeystoreCapability
}

class KeystoreSetMethodModule extends AbstractModule {
  override def configure(): Unit = {
    install(new KeystoreCapabilitiesModule())
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[KeystoreSetMethod])
  }
}

case class KeystoreCreationParseException(setError: SetError) extends Exception

class KeystoreSetMethod @Inject()(serializer: KeystoreSerializer,
                                  createPerformer: KeystoreSetCreatePerformer,
                                  destroyPerformer: KeystoreSetDestroyPerformer,
                                  val metricFactory: MetricFactory,
                                  val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[KeystoreSetRequest] {
  override val methodName: MethodName = MethodName("Keystore/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_PGP)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: KeystoreSetRequest): SMono[InvocationWithContext] = {
    for {
      created <- createPerformer.createKeys(mailboxSession, request)
      destroyed <- destroyPerformer.destroy(mailboxSession, request)
    } yield InvocationWithContext(
      invocation = Invocation(
        methodName = methodName,
        arguments = Arguments(serializer.serializeKeystoreSetResponse(KeystoreSetResponse(
          accountId = request.accountId,
          created = Some(created.retrieveCreated).filter(_.nonEmpty),
          notCreated = Some(created.retrieveErrors).filter(_.nonEmpty),
          destroyed = Some(destroyed.retrieveDestroyed.map(_.id)).filter(_.nonEmpty))).as[JsObject]),
        methodCallId = invocation.invocation.methodCallId),
      processingContext = Some(created.retrieveCreated).getOrElse(Map())
        .foldLeft(invocation.processingContext)({
          case (processingContext, (clientId, response)) =>
            Id.validate(response.id.value)
              .fold(_ => processingContext,
                serverId => processingContext.recordCreatedId(ClientId(clientId.id), ServerId(serverId)))
        }))
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, KeystoreSetRequest] =
    serializer.deserializeKeystoreSetRequest(invocation.arguments.value) match {
      case JsSuccess(keystoreSetRequest, _) => Right(keystoreSetRequest)
      case errors: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }
}
