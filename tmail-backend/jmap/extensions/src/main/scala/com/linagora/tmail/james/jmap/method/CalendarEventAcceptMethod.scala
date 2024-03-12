package com.linagora.tmail.james.jmap.method

import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.util.{Locale, UUID}

import com.linagora.tmail.james.jmap.json.CalendarEventReplySerializer
import com.linagora.tmail.james.jmap.method.CalendarEventAcceptMethod.LANGUAGE_DEFAULT
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_CALENDAR
import com.linagora.tmail.james.jmap.model.{AttendeeReply, CalendarEventNotParsable, CalendarEventParsed, CalendarEventReplyAcceptedResponse, CalendarEventReplyGenerator, CalendarEventReplyRequest, CalendarEventReplyResults, CalendarOrganizerField, CalendarParticipantsField, CalendarStartField, CalendarTitleField, InvalidCalendarFileException}
import eu.timepit.refined.auto._
import javax.annotation.PreDestroy
import javax.inject.Inject
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.PartStat
import org.apache.james.core.MailAddress
import org.apache.james.core.builder.MimeMessageBuilder
import org.apache.james.core.builder.MimeMessageBuilder.BodyPartBuilder
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Invocation, SessionTranslator}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.mail.{BlobId, BlobIds}
import org.apache.james.jmap.method.EmailSubmissionSetMethod.LOGGER
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId}
import org.apache.james.jmap.routes.{BlobNotFoundException, BlobResolvers, SessionSupplier}
import org.apache.james.lifecycle.api.{LifecycleUtil, Startable}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.queue.api.MailQueueFactory.SPOOL
import org.apache.james.queue.api.{MailQueue, MailQueueFactory}
import org.apache.james.server.core.MailImpl
import org.apache.mailet.Mail
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.{Failure, Success, Try, Using}

object CalendarEventAcceptMethod {
  val LANGUAGE_DEFAULT: Locale = Locale.ENGLISH
}

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

class CalendarEventReplyPerformer @Inject()(blobCalendarResolver: BlobCalendarResolver,
                                            mailQueueFactory: MailQueueFactory[_ <: MailQueue]) extends Startable {
  private val mailReplyGenerator: CalendarEventMailReplyGenerator = new CalendarEventMailReplyGenerator(new FakeMailCalendarEventReplyContent)

  var queue: MailQueue = _

  def init: Unit =
    queue = mailQueueFactory.createQueue(SPOOL)

  @PreDestroy
  def dispose: Unit = Try(queue.close()).recover(e => LOGGER.debug("error closing queue", e))

  def process(request: CalendarEventReplyRequest, mailboxSession: MailboxSession, partStat: PartStat): SMono[CalendarEventReplyResults] = {
    val attendeeReply: AttendeeReply = AttendeeReply(mailboxSession.getUser, partStat)
    val language: Locale = getLanguageLocale(request)

    SMono.fromCallable(() => extractParsedBlobIds(request))
      .flatMapMany { case (notParsable: CalendarEventNotParsable, parsedBlobId: Seq[BlobId]) =>
        SFlux.fromIterable(parsedBlobId)
          .flatMap(blobId => generateReplyMailAndTryEnqueue(blobId, mailboxSession, attendeeReply, language))
          .mergeWith(SFlux.just(CalendarEventReplyResults.notDone(notParsable)))
      }
      .reduce(CalendarEventReplyResults.empty)(CalendarEventReplyResults.merge)
  }

  private def extractParsedBlobIds(request: CalendarEventReplyRequest): (CalendarEventNotParsable, Seq[BlobId]) =
    request.blobIds.value.foldLeft((CalendarEventNotParsable(Set.empty), Seq.empty[BlobId])) { (resultBuilder, unparsedBlobId) =>
      BlobId.of(unparsedBlobId) match {
        case Success(blobId) => (resultBuilder._1, resultBuilder._2 :+ blobId)
        case Failure(_) => (resultBuilder._1.merge(CalendarEventNotParsable(Set(unparsedBlobId))), resultBuilder._2)
      }
    }

  private def generateReplyMailAndTryEnqueue(blobId: BlobId, mailboxSession: MailboxSession, attendeeReply: AttendeeReply, language: Locale): SMono[CalendarEventReplyResults] =
    blobCalendarResolver.resolveRequestCalendar(blobId, mailboxSession)
      .flatMap(calendarRequest => mailReplyGenerator.generateMail(calendarRequest, attendeeReply, language))
      .flatMap(replyMail => SMono(queue.enqueueReactive(replyMail))
        .`then`(SMono.fromCallable(() => LifecycleUtil.dispose(replyMail))
          .subscribeOn(Schedulers.boundedElastic()))
        .`then`(SMono.just(CalendarEventReplyResults(done = BlobIds(Seq(blobId.value))))))
      .onErrorResume({
        case _: InvalidCalendarFileException => SMono.just(CalendarEventReplyResults.notDone(blobId))
        case _: BlobNotFoundException => SMono.just(CalendarEventReplyResults.notFound(blobId))
        case e => SMono.error(e)
      })

  private def getLanguageLocale(request: CalendarEventReplyRequest): Locale = request.language.map(_.language).getOrElse(LANGUAGE_DEFAULT)
}

class BlobCalendarResolver @Inject()(blobResolvers: BlobResolvers) {
  def resolveRequestCalendar(blobId: BlobId, mailboxSession: MailboxSession): SMono[Calendar] = {
    blobResolvers.resolve(blobId, mailboxSession)
      .flatMap(blob =>
        Using(blob.content)(CalendarEventParsed.parseICal4jCalendar).toEither
          .flatMap(calendar => validate(calendar))
          .fold(error => SMono.error[Calendar](InvalidCalendarFileException(blobId, error)), SMono.just))
  }

  private def validate(calendar: Calendar): Either[IllegalArgumentException, Calendar] =
    if (calendar.getComponents("VEVENT").isEmpty) {
      Left(new IllegalArgumentException("The calendar file must contain VEVENT componennt"))
    } else if (calendar.getMethod.getValue != "REQUEST") {
      Left(new IllegalArgumentException("The calendar must have REQUEST a s a method"))
    } else {
      Right(calendar)
    }
}

class CalendarEventMailReplyGenerator(val bodyPartContentGenerator: CalendarReplyBodyPartContentGenerator) {

  private val ICS_FILE_NAME: String = "invite.ics"

  def generateMail(calendarRequest: Calendar, attendeeReply: AttendeeReply, language: Locale): SMono[Mail] =
    extractRecipient(calendarRequest)
      .fold(e => SMono.error(e), recipient => generateAttachmentPart(calendarRequest, attendeeReply)
        .flatMap(attachmentPart => bodyPartContentGenerator.getBodyPartContent(language, attendeeReply, calendarRequest)
          .map(bodyPartContent => MimeMessageBuilder.mimeMessageBuilder
            .setMultipartWithBodyParts(Seq(bodyPartContent.bodyPart).concat(attachmentPart): _*)
            .setSubject(bodyPartContent.subject)
            .build))
        .map(mimeMessage => MailImpl.builder()
          .name(generateMailName())
          .sender(attendeeReply.attendee.asString())
          .addRecipients(recipient)
          .mimeMessage(mimeMessage)
          .build()))

  private def generateAttachmentPart(calendarRequest: Calendar, attendeeReply: AttendeeReply): SMono[Seq[BodyPartBuilder]] =
    SMono.fromCallable(() => CalendarEventReplyGenerator.generate(calendarRequest, attendeeReply))
      .map(calendarReply => calendarReply.toString.getBytes(StandardCharsets.UTF_8))
      .map(calendarAsByte => Seq(MimeMessageBuilder.bodyPartBuilder
        .data(calendarAsByte)
        .addHeader("Content-Type", "text/calendar; charset=UTF-8"),
        MimeMessageBuilder.bodyPartBuilder
        .data(calendarAsByte)
        .filename(ICS_FILE_NAME)
        .disposition("attachment")
        .`type`("application/ics")))

  private def extractRecipient(calendar: Calendar): Either[Exception, MailAddress] =
    calendar.getComponents[VEvent]("VEVENT").asScala.headOption
      .flatMap(CalendarOrganizerField.from)
      .flatMap(_.mailto) match {
      case Some(value) => scala.Right(value)
      case None => scala.Left(new IllegalArgumentException("Cannot extract the organizer from the calendar event."))
    }

  private def generateMailName(): String = "calendar-reply-" + UUID.randomUUID().toString
}

case class ReplyBodyPart(subject: String, bodyPart: BodyPartBuilder)

trait CalendarReplyBodyPartContentGenerator {
  def getBodyPartContent(i18n: Locale, attendeeReply: AttendeeReply, calendar: Calendar): SMono[ReplyBodyPart]
}

class FakeMailCalendarEventReplyContent extends CalendarReplyBodyPartContentGenerator {

  val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM dd, yyyy")

  override def getBodyPartContent(i18n: Locale, attendeeReply: AttendeeReply, calendar: Calendar): SMono[ReplyBodyPart] = {
    SMono.just(attendeeReply.attendee.asMailAddress().toString + " has " + attendeeReply.partStat.getValue.toLowerCase + " this invitation")
      .map(MimeMessageBuilder.bodyPartBuilder.data(_))
      .map(bodyPart => ReplyBodyPart(generateSubject(attendeeReply, calendar), bodyPart))
  }

  private def generateSubject(attendeeReply: AttendeeReply, calendar: Calendar): String = {
    val event: VEvent = calendar.getComponents[VEvent]("VEVENT").asScala.head
    val eventTitle: Option[CalendarTitleField] = CalendarTitleField.from(event)
    val startDate: Option[CalendarStartField] = CalendarStartField.from(event)
    s"${attendeeReply.partStat.getValue}: " + eventTitle.map(_.value).getOrElse("") + startDate.map(sd => " @ " + sd.value.format(dateTimeFormatter)).getOrElse("") + " (" + attendeeReply.attendee.asMailAddress().toString + ")"
  }
}