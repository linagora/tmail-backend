package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.CalendarEventAttendanceSerializer
import com.linagora.tmail.james.jmap.method.CalendarEventAttendanceGetRequest.MAXIMUM_NUMBER_OF_BLOB_IDS
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_CALENDAR
import com.linagora.tmail.james.jmap.model.{CalendarEventAttendanceResults, CalendarEventNotDone, CalendarEventNotFound, CalendarEventNotParsable}
import com.linagora.tmail.james.jmap.{AttendanceStatus, EventAttendanceRepository}
import eu.timepit.refined.auto._
import eu.timepit.refined.refineV
import jakarta.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{AccountId, Invocation, SessionTranslator}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.mail.{BlobId, BlobIds, RequestTooLargeException}
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId, WithAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

class CalendarEventAttendanceGetMethod @Inject()(val eventAttendanceRepository: EventAttendanceRepository,
                                       val metricFactory: MetricFactory,
                                       val sessionSupplier: SessionSupplier,
                                       val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[CalendarEventAttendanceGetRequest] {
  override val methodName: Invocation.MethodName = MethodName("CalendarEventAttendance/get")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_CALENDAR)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: CalendarEventAttendanceGetRequest): Publisher[InvocationWithContext] =
    CalendarEventAttendanceGetRequest.extractParsedBlobIds(request) match {
      case (notParsable: CalendarEventNotParsable, blobIdList: Seq[BlobId]) =>
        SMono(eventAttendanceRepository.getAttendanceStatus(mailboxSession.getUser, blobIdList.asJava))
          .map(result => CalendarEventAttendanceResults.merge(result, CalendarEventAttendanceResults.notDone(notParsable)))
          .map(result => CalendarEventAttendanceGetResponse.from(request.accountId, result))
          .map(response => Invocation(
            methodName,
            Arguments(CalendarEventAttendanceSerializer.serializeEventAttendanceGetResponse(response).as[JsObject]),
            invocation.invocation.methodCallId))
          .map(InvocationWithContext(_, invocation.processingContext))
    }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, CalendarEventAttendanceGetRequest] =
    CalendarEventAttendanceSerializer.deserializeEventAttendanceGetRequest(invocation.arguments.value)
      .asEither.left.map(ResponseSerializer.asException)
      .flatMap(_.validate())
}

object CalendarEventAttendanceGetRequest {
  val MAXIMUM_NUMBER_OF_BLOB_IDS: Int = 16

  def extractParsedBlobIds(request: CalendarEventAttendanceGetRequest): (CalendarEventNotParsable, Seq[BlobId]) =
    request.blobIds.value.foldLeft((CalendarEventNotParsable(Set.empty), Seq.empty[BlobId])) { (resultBuilder, unparsedBlobId) =>
      BlobId.of(unparsedBlobId) match {
        case Success(blobId) => (resultBuilder._1, resultBuilder._2 :+ blobId)
        case Failure(_) => (resultBuilder._1.merge(CalendarEventNotParsable(Set(unparsedBlobId))), resultBuilder._2)
      }
    }
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
    val list: List[CalendarEventAttendanceRecord] = results.done.map { entry =>
      val refinedBlobId = refineV[IdConstraint](entry.blobId).fold(
        _ => BlobId("invalid"), // Fallback for invalid values
        validBlobId => BlobId(validBlobId))
      CalendarEventAttendanceRecord(refinedBlobId, entry.eventAttendanceStatus, entry.isFree)
    }

    CalendarEventAttendanceGetResponse(
      accountId,
      list,
      results.notFound,
      results.notDone)
  }
}

case class CalendarEventAttendanceRecord(blobId: BlobId,
                                         eventAttendanceStatus: AttendanceStatus,
                                         isFree: Option[Boolean] = None)

case class CalendarEventAttendanceGetResponse(accountId: AccountId,
                                              list: List[CalendarEventAttendanceRecord] = List(),
                                              notFound: Option[CalendarEventNotFound] = Option.empty,
                                              notDone: Option[CalendarEventNotDone] = Option.empty) extends WithAccountId

