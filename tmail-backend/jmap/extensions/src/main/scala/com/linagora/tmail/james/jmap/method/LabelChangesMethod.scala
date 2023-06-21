package com.linagora.tmail.james.jmap.method

import com.google.inject.Inject
import com.linagora.tmail.james.jmap.json.{LabelChangesSerializer => Serializer}
import com.linagora.tmail.james.jmap.label.{LabelChangeRepository, LabelChanges}
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_LABEL
import com.linagora.tmail.james.jmap.model.{LabelChangesRequest => Request, LabelChangesResponse => Response}
import eu.timepit.refined.auto._
import org.apache.james.jmap.api.change.{State => JavaState}
import org.apache.james.jmap.api.model.{AccountId => JavaAccountId}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Invocation, SessionTranslator}
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class LabelChangesMethod @Inject()(val metricFactory: MetricFactory,
                                   val sessionSupplier: SessionSupplier,
                                   val sessionTranslator: SessionTranslator,
                                   val labelChangeRepository: LabelChangeRepository) extends MethodRequiringAccountId[Request] {

  override val methodName: Invocation.MethodName = MethodName("Label/changes")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_LABEL)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: Request): Publisher[InvocationWithContext] =
    retrieveChanges(mailboxSession, request)
      .map(Response.from(request.accountId, request.sinceState, _))
      .map(res => InvocationWithContext(
        invocation = Invocation(
          methodName = methodName,
          arguments = Arguments(Serializer.serialize(res)),
          methodCallId = invocation.invocation.methodCallId),
        processingContext = invocation.processingContext))

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, Request] =
    Serializer.deserializeRequest(invocation.arguments.value).asEitherRequest

  private def retrieveChanges(mailboxSession: MailboxSession, request: Request): SMono[LabelChanges] =
    SMono(labelChangeRepository.getSinceState(
      accountId = JavaAccountId.fromUsername(mailboxSession.getUser),
      state = JavaState.of(request.sinceState.value),
      maxIdsToReturn = request.maxChanges))
}
