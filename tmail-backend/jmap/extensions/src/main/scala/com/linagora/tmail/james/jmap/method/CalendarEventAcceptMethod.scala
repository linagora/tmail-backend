package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.CalendarEventReplySerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_CALENDAR
import com.linagora.tmail.james.jmap.model.{CalendarEventReplyAcceptedResponse, CalendarEventReplyRequest}
import eu.timepit.refined.auto._
import javax.inject.Inject
import net.fortuna.ical4j.model.parameter.PartStat
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Invocation, SessionTranslator}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject

class CalendarEventAcceptMethod @Inject()(val calendarEventReplyPerformer: CalendarEventReplyPerformer,
                                          val metricFactory: MetricFactory,
                                          val sessionTranslator: SessionTranslator,
                                          val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[CalendarEventReplyRequest] {


  override val methodName: Invocation.MethodName = MethodName("CalendarEvent/accept")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_CALENDAR)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, CalendarEventReplyRequest] =
    CalendarEventReplySerializer.deserializeRequest(invocation.arguments.value)
      .asEither.left.map(ResponseSerializer.asException)
      .flatMap(_.validate)

  override def doProcess(capabilities: Set[CapabilityIdentifier],
                         invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: CalendarEventReplyRequest): Publisher[InvocationWithContext] = {
    calendarEventReplyPerformer.process(request, mailboxSession, PartStat.ACCEPTED)
      .map(result => CalendarEventReplyAcceptedResponse.from(request.accountId, result))
      .map(response => Invocation(
        methodName,
        Arguments(CalendarEventReplySerializer.serialize(response).as[JsObject]),
        invocation.invocation.methodCallId))
      .map(InvocationWithContext(_, invocation.processingContext))
  }
}
