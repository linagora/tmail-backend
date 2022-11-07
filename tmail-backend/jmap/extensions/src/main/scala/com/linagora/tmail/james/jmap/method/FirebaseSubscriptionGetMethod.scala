package com.linagora.tmail.james.jmap.method

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Inject}
import com.linagora.tmail.james.jmap.firebase.FirebaseSubscriptionRepository
import com.linagora.tmail.james.jmap.json.FirebaseSubscriptionSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_FIREBASE
import com.linagora.tmail.james.jmap.model.{FirebaseSubscription, FirebaseSubscriptionGetRequest, FirebaseSubscriptionGetResponse, FirebaseSubscriptionId, FirebaseSubscriptionIds}
import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodWithoutAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsObject}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class FirebaseSubscriptionGetMethodModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[FirebaseSubscriptionGetMethod])
  }
}

class FirebaseSubscriptionGetMethod @Inject()(val repository: FirebaseSubscriptionRepository,
                                              val metricFactory: MetricFactory,
                                              val sessionSupplier: SessionSupplier) extends MethodWithoutAccountId[FirebaseSubscriptionGetRequest] {

  override val methodName: Invocation.MethodName = MethodName("FirebaseRegistration/get")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_FIREBASE)

  override def getRequest(invocation: Invocation): Either[Exception, FirebaseSubscriptionGetRequest] =
    FirebaseSubscriptionSerializer.deserializeFirebaseSubscriptionGetRequest(invocation.arguments.value).asEither
      .left.map(errors => new IllegalArgumentException(ResponseSerializer.serialize(JsError(errors)).toString))

  override def doProcess(invocation: InvocationWithContext, mailboxSession: MailboxSession, request: FirebaseSubscriptionGetRequest): Publisher[InvocationWithContext] =
    request.validateProperties
      .fold(e => SMono.error(e),
        properties => retrieveFirebaseSubscription(mailboxSession.getUser, request.ids)
          .collectSeq()
          .map(firebaseSubscriptions => FirebaseSubscriptionGetResponse.from(firebaseSubscriptions, request.ids))
          .map(response => Invocation(
            methodName = methodName,
            arguments = Arguments(FirebaseSubscriptionSerializer.serialize(response, properties).as[JsObject]),
            methodCallId = invocation.invocation.methodCallId))
          .map(invocationResult => InvocationWithContext(invocationResult, invocation.processingContext)))

  private def retrieveFirebaseSubscription(username: Username, ids: Option[FirebaseSubscriptionIds]): SFlux[FirebaseSubscription] =
    ids match {
      case None => SFlux(repository.list(username))
      case Some(value) =>
        SFlux(repository.get(username,
          value.list.map(id => FirebaseSubscriptionId.liftOrThrow(id.id.value).toOption)
            .filter(_.isDefined)
            .map(_.get)
            .toSet.asJava))
    }
}
