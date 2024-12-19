package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.CalendarEventReplySerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_CALENDAR
import com.linagora.tmail.james.jmap.model.{CalendarEventReplyAcceptedResponse, CalendarEventReplyRequest}
import com.linagora.tmail.james.jmap.{AttendanceStatus, EventAttendanceRepository}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
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
import reactor.core.publisher.Mono

import scala.compat.java8.OptionConverters

class CalendarEventAcceptMethod @Inject()(val eventAttendanceRepository: EventAttendanceRepository,
                                          val metricFactory: MetricFactory,
                                          val sessionTranslator: SessionTranslator,
                                          val sessionSupplier: SessionSupplier,
                                          val supportedLanguage: CalendarEventReplySupportedLanguage) extends MethodRequiringAccountId[CalendarEventReplyRequest] {


  override val methodName: Invocation.MethodName = MethodName("CalendarEvent/accept")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_CALENDAR)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, CalendarEventReplyRequest] =
    CalendarEventReplySerializer.deserializeRequest(invocation.arguments.value)
      .asEither.left.map(ResponseSerializer.asException)
      .flatMap(_.validate(supportedLanguage.valueAsStringSet))

  override def doProcess(capabilities: Set[CapabilityIdentifier],
                         invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: CalendarEventReplyRequest): Publisher[InvocationWithContext] = {
    Mono.from(eventAttendanceRepository.setAttendanceStatus(mailboxSession.getUser, AttendanceStatus.Accepted, request.blobIds, OptionConverters.toJava(request.language)))
      .map(result => CalendarEventReplyAcceptedResponse.from(request.accountId, result))
          .map(response => Invocation(
            methodName,
            Arguments(CalendarEventReplySerializer.serialize(response).as[JsObject]),
            invocation.invocation.methodCallId))
          .map(InvocationWithContext(_, invocation.processingContext))
  }
}
