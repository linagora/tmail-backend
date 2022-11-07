package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.FirebaseSubscriptionSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_FIREBASE
import com.linagora.tmail.james.jmap.method.FirebaseSubscriptionSetCreatePerformer.CreationResults
import com.linagora.tmail.james.jmap.model.{FirebaseSubscriptionSetRequest, FirebaseSubscriptionSetResponse}
import eu.timepit.refined.auto._
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{ClientId, Invocation, ServerId}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, MethodWithoutAccountId}
import org.apache.james.jmap.routes.{ProcessingContext, SessionSupplier}
import org.apache.james.lifecycle.api.Startable
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.JsError

import javax.inject.Inject

class FirebaseSubscriptionSetMethod @Inject()(val serializer: FirebaseSubscriptionSerializer,
                                              val createPerformer: FirebaseSubscriptionSetCreatePerformer,
                                              val metricFactory: MetricFactory,
                                              val sessionSupplier: SessionSupplier) extends MethodWithoutAccountId[FirebaseSubscriptionSetRequest] with Startable {

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_FIREBASE)
  override val methodName: MethodName = MethodName("FirebaseRegistration/set")

  override def getRequest(invocation: Invocation): Either[Exception, FirebaseSubscriptionSetRequest] =
    serializer.deserializeFirebaseSubscriptionSetRequest(invocation.arguments.value).asEither
      .left.map(errors => new IllegalArgumentException(ResponseSerializer.serialize(JsError(errors)).toString))

  override def doProcess(invocation: InvocationWithContext, mailboxSession: MailboxSession, request: FirebaseSubscriptionSetRequest): Publisher[InvocationWithContext] = {
    for {
      created <- createPerformer.create(request, mailboxSession.getUser)
    } yield InvocationWithContext(
      invocation = Invocation(
        methodName = methodName,
        arguments = Arguments(serializer.serialize(FirebaseSubscriptionSetResponse(
          created = created.created.filter(_.nonEmpty),
          notCreated = created.notCreated.filter(_.nonEmpty)))),
        methodCallId = invocation.invocation.methodCallId),
      processingContext = recordCreationIdInProcessingContext(created, invocation.processingContext))
  }
  private def recordCreationIdInProcessingContext(results: CreationResults, processingContext: ProcessingContext):ProcessingContext = {

    results.created.getOrElse(Map())
      .foldLeft(processingContext)({
        case (processingContext, (creationId, result)) =>
          processingContext.recordCreatedId(ClientId(creationId.id), ServerId(result.id.asUnparsedFirebaseSubscriptionId.id))
      })
  }

}
