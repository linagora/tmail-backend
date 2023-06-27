package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.LabelSerializer
import com.linagora.tmail.james.jmap.label.LabelChangesPopulate
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_LABEL
import com.linagora.tmail.james.jmap.model.{LabelSetRequest, LabelSetResponse}
import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.api.model.{AccountId => JavaAccountId}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{ClientId, Id, Invocation, ServerId, SessionTranslator, UuidState}
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.SMono

class LabelSetMethod @Inject() (val createPerformer: LabelSetCreatePerformer,
                                val updatePerformer: LabelSetUpdatePerformer,
                                val deletePerformer: LabelSetDeletePerformer,
                                val labelChangesPopulate: LabelChangesPopulate,
                                val metricFactory: MetricFactory,
                                val sessionTranslator: SessionTranslator,
                                val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[LabelSetRequest] {
  override val methodName: Invocation.MethodName = MethodName("Label/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_LABEL)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: LabelSetRequest): Publisher[InvocationWithContext] =
    (for {
      oldState <- retrieveState(mailboxSession)
      created <- createPerformer.createLabels(mailboxSession, request)
      updated <- updatePerformer.update(request, mailboxSession.getUser)
      destroyed <- deletePerformer.deleteLabels(request, mailboxSession)
    } yield {
      labelChangesPopulate.populate(mailboxSession.getUser, created, destroyed, updated)
        .switchIfEmpty(SMono.just(oldState))
        .map(newState => InvocationWithContext(
            invocation = Invocation(
              methodName = methodName,
              arguments = Arguments(LabelSerializer.serializeLabelSetResponse(LabelSetResponse(
                accountId = request.accountId,
                oldState = Some(oldState),
                newState = newState,
                created = Some(created.retrieveCreated).filter(_.nonEmpty),
                notCreated = Some(created.retrieveErrors).filter(_.nonEmpty),
                updated = Some(updated.updated).filter(_.nonEmpty),
                notUpdated = Some(updated.notUpdated).filter(_.nonEmpty),
                destroyed = Some(destroyed.destroyed).filter(_.nonEmpty),
                notDestroyed = Some(destroyed.retrieveErrors).filter(_.nonEmpty))).as[JsObject]),
              methodCallId = invocation.invocation.methodCallId),
            processingContext = Some(created.retrieveCreated).getOrElse(Map())
              .foldLeft(invocation.processingContext)({
                case (processingContext, (clientId, response)) =>
                  Id.validate(response.id.id)
                    .fold(_ => processingContext,
                      serverId => processingContext.recordCreatedId(ClientId(clientId.id), ServerId(serverId)))
              })))
    })
      .flatMap(publisher => publisher)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, LabelSetRequest] =
    LabelSerializer.deserializeLabelSetRequest(invocation.arguments.value).asEitherRequest

  private def retrieveState(mailboxSession: MailboxSession): SMono[UuidState] =
    SMono(labelChangesPopulate.labelChangeRepository.getLatestState(JavaAccountId.fromUsername(mailboxSession.getUser)))
      .map(UuidState.fromJava)
}
