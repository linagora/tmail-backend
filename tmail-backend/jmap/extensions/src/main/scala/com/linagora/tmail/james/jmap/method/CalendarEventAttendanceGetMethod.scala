package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.CalendarEventAttendanceSerializer
import com.linagora.tmail.james.jmap.method.CalendarEventAttendanceGetRequest.MAXIMUM_NUMBER_OF_BLOB_IDS
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_CALENDAR
import com.linagora.tmail.james.jmap.model.{CalendarEventAttendanceResults, CalendarEventNotDone, CalendarEventNotFound, CalendarEventNotParsable, InvalidCalendarFileException}
import com.linagora.tmail.james.jmap.{AttendanceStatus, EventAttendanceRepository}
import eu.timepit.refined.auto._
import eu.timepit.refined.refineV
import jakarta.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, Invocation, SessionTranslator, SetError}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.mail.{BlobId, BlobIds, RequestTooLargeException}
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId, WithAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.SMono

class CalendarEventAttendanceGetMethod @Inject()(val eventAttendanceRepository: EventAttendanceRepository,
                                       val metricFactory: MetricFactory,
                                       val sessionSupplier: SessionSupplier,
                                       val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[CalendarEventAttendanceGetRequest] {
  override val methodName: Invocation.MethodName = MethodName("CalendarEventAttendance/get")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_CALENDAR)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: CalendarEventAttendanceGetRequest): Publisher[InvocationWithContext] =
    SMono.fromDirect(eventAttendanceRepository.getAttendanceStatus(mailboxSession.getUser, request.blobIds))
      .map(result => CalendarEventAttendanceGetResponse.from(request.accountId, result))
      .map(response => Invocation(
        methodName,
        Arguments(CalendarEventAttendanceSerializer.serializeEventAttendanceGetResponse(response).as[JsObject]),
        invocation.invocation.methodCallId))
      .map(InvocationWithContext(_, invocation.processingContext))

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, CalendarEventAttendanceGetRequest] =
    CalendarEventAttendanceSerializer.deserializeEventAttendanceGetRequest(invocation.arguments.value)
      .asEither.left.map(ResponseSerializer.asException)
      .flatMap(_.validate())
}

object CalendarEventAttendanceGetRequest {
  val MAXIMUM_NUMBER_OF_BLOB_IDS: Int = 16
}

case class CalendarEventAttendanceGetRequest(accountId: AccountId,
                                             blobIds: BlobIds) extends WithAccountId {
  def validate(): Either[Exception, CalendarEventAttendanceGetRequest] =
    validateBlobIdsSize

  private def validateBlobIdsSize: Either[RequestTooLargeException, CalendarEventAttendanceGetRequest] =
    if (blobIds.value.length > MAXIMUM_NUMBER_OF_BLOB_IDS) {
      Left(RequestTooLargeException("The number of ids requested by the client exceeds the maximum number the server is willing to process in a single method call"))
    } else {
      scala.Right(this)
    }
}

object CalendarEventAttendanceGetResponse {
  def from(accountId: AccountId, results: CalendarEventAttendanceResults): CalendarEventAttendanceGetResponse = {
    val (accepted, rejected, tentativelyAccepted, needsAction) =
      results.done.foldLeft((List.empty[BlobId], List.empty[BlobId], List.empty[BlobId], List.empty[BlobId])) {
        case ((accepted, rejected, tentativelyAccepted, needsAction), entry) =>
          val refinedBlobId = refineV[IdConstraint](entry.blobId).fold(
            _ => BlobId("invalid"),  // Fallback for invalid values
            validBlobId => BlobId(validBlobId)
          )
          entry.eventAttendanceStatus match {
            case AttendanceStatus.Accepted => (refinedBlobId :: accepted, rejected, tentativelyAccepted, needsAction)
            case AttendanceStatus.Declined => (accepted, refinedBlobId :: rejected, tentativelyAccepted, needsAction)
            case AttendanceStatus.Tentative => (accepted, rejected, refinedBlobId :: tentativelyAccepted, needsAction)
            case AttendanceStatus.NeedsAction => (accepted, rejected, tentativelyAccepted, refinedBlobId :: needsAction)
          }
      }

    CalendarEventAttendanceGetResponse(
      accountId,
      accepted,
      rejected,
      tentativelyAccepted,
      needsAction,
      results.notFound,
      results.notDone)
  }
}

case class CalendarEventAttendanceGetResponse(accountId: AccountId,
                                              accepted: List[BlobId] = List(),
                                              rejected: List[BlobId] = List(),
                                              tentativelyAccepted: List[BlobId] = List(),
                                              needsAction: List[BlobId] = List(),
                                              notFound: Option[CalendarEventNotFound] = Option.empty,
                                              notDone: Option[CalendarEventNotDone] = Option.empty) extends WithAccountId

