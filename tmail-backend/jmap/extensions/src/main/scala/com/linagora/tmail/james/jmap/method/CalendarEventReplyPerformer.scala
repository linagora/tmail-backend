package com.linagora.tmail.james.jmap.method

import java.io.{FileNotFoundException, StringReader, StringWriter}
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.util.{Locale, UUID, Map => JavaMap}

import com.github.mustachejava.{DefaultMustacheFactory, MustacheFactory}
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableMap
import com.linagora.tmail.james.jmap.method.CalendarEventReplyMustacheFactory.MUSTACHE_FACTORY
import com.linagora.tmail.james.jmap.method.CalendarEventReplyPerformer.I18N_MAIL_TEMPLATE_LOCATION_PROPERTY
import com.linagora.tmail.james.jmap.model.{AttendeeReply, CalendarAttendeeField, CalendarEndField, CalendarEventNotParsable, CalendarEventParsed, CalendarEventReplyGenerator, CalendarEventReplyRequest, CalendarEventReplyResults, CalendarLocationField, CalendarOrganizerField, CalendarParticipantsField, CalendarStartField, CalendarTitleField, InvalidCalendarFileException, LanguageLocation}
import eu.timepit.refined.auto._
import jakarta.mail.Part
import jakarta.mail.internet.{MimeMessage, MimeMultipart}
import javax.annotation.PreDestroy
import javax.inject.{Inject, Named}
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.PartStat
import org.apache.commons.configuration2.Configuration
import org.apache.james.core.MailAddress
import org.apache.james.core.builder.MimeMessageBuilder
import org.apache.james.core.builder.MimeMessageBuilder.BodyPartBuilder
import org.apache.james.filesystem.api.FileSystem
import org.apache.james.jmap.mail.{BlobId, BlobIds}
import org.apache.james.jmap.method.EmailSubmissionSetMethod.LOGGER
import org.apache.james.jmap.routes.{BlobNotFoundException, BlobResolvers}
import org.apache.james.lifecycle.api.{LifecycleUtil, Startable}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.queue.api.MailQueueFactory.SPOOL
import org.apache.james.queue.api.{MailQueue, MailQueueFactory}
import org.apache.james.server.core.{MailImpl, MimeMessageInputStreamSource, MimeMessageWrapper, MissingArgumentException}
import org.apache.james.utils.PropertiesProvider
import org.apache.mailet.Mail
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.{Failure, Success, Try, Using}

object CalendarEventReplyPerformer {
  val I18N_MAIL_TEMPLATE_LOCATION_PROPERTY: String = "calendarEvent.reply.mailTemplateLocation"
  val SUPPORTED_LANGUAGES_PROPERTY: String = "calendarEvent.reply.supportedLanguages"
}

class CalendarEventReplyPerformer @Inject()(blobCalendarResolver: BlobCalendarResolver,
                                            mailQueueFactory: MailQueueFactory[_ <: MailQueue],
                                            fileSystem: FileSystem,
                                            @Named("jmap") jmapConfiguration: Configuration,
                                            supportedLanguage: CalendarEventReplySupportedLanguage) extends Startable {

  private val mailReplyGenerator: CalendarEventMailReplyGenerator = Try(jmapConfiguration.getString(I18N_MAIL_TEMPLATE_LOCATION_PROPERTY))
    .map(i18nEmlDirectory => new I18NCalendarEventReplyMessageGenerator(fileSystem, i18nEmlDirectory)) match {
    case Success(value) => new CalendarEventMailReplyGenerator(value)
    case Failure(_) => throw new MissingArgumentException("JMAP CalendarEvent needs a " + I18N_MAIL_TEMPLATE_LOCATION_PROPERTY + " entry")
  }

  var queue: MailQueue = _

  def init: Unit =
    queue = mailQueueFactory.createQueue(SPOOL)

  @PreDestroy
  def dispose: Unit = Try(queue.close()).recover(e => LOGGER.debug("error closing queue", e))

  def process(request: CalendarEventReplyRequest, mailboxSession: MailboxSession, partStat: PartStat): SMono[CalendarEventReplyResults] = {
    val attendeeReply: AttendeeReply = AttendeeReply(mailboxSession.getUser, partStat)
    val language: Locale = getLanguageLocale(request)
    Preconditions.checkArgument(supportedLanguage.isSupported(language), s"The language only supports ${supportedLanguage.value}".asInstanceOf[Object])

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

  private def getLanguageLocale(request: CalendarEventReplyRequest): Locale =
    request.language.map(_.language).getOrElse(CalendarEventReplySupportedLanguage.LANGUAGE_DEFAULT)
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

private object CalendarEventReplySupportedLanguage {
  val LANGUAGE_DEFAULT: Locale = Locale.ENGLISH
}

class CalendarEventReplySupportedLanguage @Inject()(propertiesProvider: PropertiesProvider) {

  private val supportedLanguages: Set[Locale] = getSupportedLanguagesConfiguration match {
    case supportedLanguages if supportedLanguages.isEmpty => Set(CalendarEventReplySupportedLanguage.LANGUAGE_DEFAULT)
    case supportedLanguages => supportedLanguages
  }

  private def getSupportedLanguagesConfiguration: Set[Locale] =
    Try(propertiesProvider.getConfiguration("jmap"))
      .map(configuration => configuration.getStringArray(CalendarEventReplyPerformer.SUPPORTED_LANGUAGES_PROPERTY).toSet)
      .map(_.map(lgTag => LanguageLocation.detectLocale(lgTag) match {
        case Success(value) => value
        case Failure(error) => throw new MissingArgumentException("Invalid language tag in the configuration file." + error.getMessage)
      })).fold(_ => Set.empty, identity)

  def value: Set[Locale] = supportedLanguages

  def valueAsStringSet: Set[String] = supportedLanguages.map(_.getLanguage)

  def isSupported(language: Locale): Boolean = supportedLanguages.contains(language)
}

class CalendarEventMailReplyGenerator(val bodyPartContentGenerator: CalendarReplyMessageGenerator) {

  private val ICS_FILE_NAME: String = "invite.ics"

  def generateMail(calendarRequest: Calendar, attendeeReply: AttendeeReply, language: Locale): SMono[Mail] =
    extractRecipient(calendarRequest)
      .fold(e => SMono.error(e), recipient => generateAttachmentPart(calendarRequest, attendeeReply)
        .flatMap(attachmentPart => bodyPartContentGenerator.getBasedMimeMessage(language, attendeeReply, calendarRequest)
          .map(decoratedMimeMessage => appendAttachmentPartToMail(attachmentPart, decoratedMimeMessage)))
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

  private def appendAttachmentPartToMail(attachmentPart: Seq[BodyPartBuilder], mimeMessage: MimeMessage): MimeMessage = {
    mimeMessage.getContent match {
      case mimeMultipart: MimeMultipart =>
        attachmentPart.foreach(part => mimeMultipart.addBodyPart(part.build))
        mimeMessage.setContent(mimeMultipart)
      case singleTextPart: String =>
        val mimeMultipart = new MimeMultipart
        val textBodyPart = MimeMessageBuilder.bodyPartBuilder
          .data(singleTextPart)
          .`type`(mimeMessage.getContentType)
          .build()
        mimeMultipart.addBodyPart(textBodyPart)
        attachmentPart.foreach(part => mimeMultipart.addBodyPart(part.build))
        mimeMessage.setContent(mimeMultipart)
      case _ => throw new IllegalArgumentException("The eml file must contain a text body.")
    }
    mimeMessage.saveChanges()
    mimeMessage
  }
}

trait CalendarReplyMessageGenerator {
  def getBasedMimeMessage(i18n: Locale, attendeeReply: AttendeeReply, calendar: Calendar): SMono[MimeMessage]
}

private object I18NCalendarEventReplyMessageGenerator {
  private object MUSTACHE {
    val PART_STAT: String = "PART_STAT"
    val ATTENDEE: String = "ATTENDEE"
    val ATTENDEE_CN: String = "ATTENDEE_CN"
    val EVENT_ORGANIZER: String = "ORGANIZER"
    val EVENT_ORGANIZER_CN: String = "ORGANIZER_CN"
    val EVENT_TITLE: String = "EVENT_TITLE"
    val EVENT_START_DATE: String = "EVENT_START_DATE"
    val EVENT_END_DATE: String = "EVENT_END_DATE"
    val EVENT_LOCATION: String = "EVENT_LOCATION"
  }

  private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM dd, yyyy")
}

class I18NCalendarEventReplyMessageGenerator(fileSystem: FileSystem, i18nEmlDirectory: String) extends CalendarReplyMessageGenerator {

  import I18NCalendarEventReplyMessageGenerator._

  override def getBasedMimeMessage(i18n: Locale, attendeeReply: AttendeeReply, requestCalendar: Calendar): SMono[MimeMessage] =
    getBasedMimeMessage(attendeeReply, requestCalendar, evaluateMailTemplateFileName(attendeeReply.partStat, i18n))

  def getBasedMimeMessage(attendeeReply: AttendeeReply, calendar: Calendar, emlFilename: String): SMono[MimeMessage] =
    SMono.fromCallable(() => URI.create(i18nEmlDirectory).resolve(emlFilename).toString)
      .doOnNext(validateTemplateFile)
      .map(emlLocation => new MimeMessageWrapper(MimeMessageInputStreamSource.create(MailImpl.getId, fileSystem.getResource(emlLocation))))
      .map(mimeMessage => decoratedMimeMessage(mimeMessage, getMustacheDataMap(attendeeReply, calendar)))
      .subscribeOn(Schedulers.boundedElastic())

  private def decoratedMimeMessage(originalMimeMessage: MimeMessageWrapper, mustacheDataMap: JavaMap[String, String]): MimeMessage = {
    originalMimeMessage.loadMessage()
    originalMimeMessage.getContent match {
      case textBody: String =>
        val decoratedBody = decorateTextBodyMessage(textBody, mustacheDataMap)
        originalMimeMessage.setContent(decoratedBody, originalMimeMessage.getContentType)
      case mimeMultipart: MimeMultipart =>
        val partNumber = mimeMultipart.getCount
        for (i <- 0 until partNumber) {
          val bodyPart = mimeMultipart.getBodyPart(i)
          if (bodyPart.getDisposition == null || bodyPart.getDisposition.equalsIgnoreCase(Part.INLINE)) {
            if (bodyPart.isMimeType("text/plain") || bodyPart.isMimeType("text/html")) {
              val decoratedBody = decorateTextBodyMessage(bodyPart.getContent.asInstanceOf[String], mustacheDataMap)
              bodyPart.setContent(decoratedBody, bodyPart.getContentType)
            }
          }
        }
      case _ => throw new IllegalArgumentException("The eml file must contain a text body.")
    }
    originalMimeMessage.setSubject(decorateSubjectMessage(originalMimeMessage.getSubject, mustacheDataMap))
    originalMimeMessage.saveChanges()
    originalMimeMessage
  }

  private def validateTemplateFile(emlLocation: String): Unit =
    try {
      fileSystem.getFile(emlLocation)
    } catch {
      case e: FileNotFoundException => throw new IllegalArgumentException(s"Cannot find the template eml file: $emlLocation", e)
      case e: Exception => throw new IllegalArgumentException(s"Cannot read the template eml file: $emlLocation", e)
    }

  private def getMustacheDataMap(attendeeReply: AttendeeReply, calendar: Calendar): JavaMap[String, String] = {
    val event: VEvent = calendar.getComponents[VEvent]("VEVENT").asScala.head
    val eventTitle: Option[CalendarTitleField] = CalendarTitleField.from(event)
    val startDate: Option[CalendarStartField] = CalendarStartField.from(event)
    val endDate: Option[CalendarEndField] = CalendarEndField.from(event)
    val location: Option[CalendarLocationField] = CalendarLocationField.from(event)

    ImmutableMap.builder[String, String]
      .putAll(getAttendeeMustache(event, attendeeReply))
      .putAll(getOrganizerMustache(event))
      .putAll(ImmutableMap.of(
        MUSTACHE.PART_STAT, attendeeReply.partStat.getValue,
        MUSTACHE.EVENT_TITLE, eventTitle.map(_.value).getOrElse(""),
        MUSTACHE.EVENT_START_DATE, startDate.map(sd => sd.value.format(DATE_TIME_FORMATTER)).getOrElse(""),
        MUSTACHE.EVENT_END_DATE, endDate.map(ed => ed.value.format(DATE_TIME_FORMATTER)).getOrElse(""),
        MUSTACHE.EVENT_LOCATION, location.map(_.value).getOrElse("")))
      .build()
  }

  def evaluateMailTemplateFileName(partStat: PartStat, language: Locale): String =
    s"calendar_reply_${partStat.getValue.toLowerCase}-${language.getLanguage}.eml"
  def getAttendeeMustache(event: VEvent, attendeeReply: AttendeeReply): JavaMap[String, String] = {
    val attendeeInRequest: Option[CalendarAttendeeField] = CalendarParticipantsField.from(event)
      .findParticipantByMailTo(attendeeReply.attendee.asString())

    val (attendeeMustache, attendeeCNMustache) = attendeeInRequest match {
      case Some(attendee) =>
        val attendeeMustache: String = (attendee.name, attendee.mailto) match {
          case (Some(n), Some(m)) => s"${n.value} ${m.value.asPrettyString()}"
          case (_, Some(m)) => m.value.toString
        }
        val attendeeNameMustache: String = attendee.name.map(_.value).getOrElse("")
        (attendeeMustache, attendeeNameMustache)
      case None => ("", "")
    }

    ImmutableMap.of(MUSTACHE.ATTENDEE, attendeeMustache, MUSTACHE.ATTENDEE_CN, attendeeCNMustache)
  }

  def getOrganizerMustache(event: VEvent): JavaMap[String, String] = {
    val (organizerCn, organizer) = CalendarOrganizerField.from(event)
      .map(e => (e.name, e.mailto) match {
        case (Some(n), Some(m)) => (n, s"$n ${m.asPrettyString()}")
        case (_, Some(m)) => ("", m.toString)
      }).getOrElse(("", ""))
    ImmutableMap.of(MUSTACHE.EVENT_ORGANIZER, organizer, MUSTACHE.EVENT_ORGANIZER_CN, organizerCn)
  }

  private def decorateSubjectMessage(mustacheSubject: String, dataMap: JavaMap[String, String]): String =
    replaceMustache(mustacheSubject, dataMap)

  private def decorateTextBodyMessage(mustacheTextBody: String, dataMap: JavaMap[String, String]): String =
    replaceMustache(mustacheTextBody, dataMap)

  private def replaceMustache(input: String, dataMap: JavaMap[String, String]): String = {
    val mustache = MUSTACHE_FACTORY.compile(new StringReader(input), "example")
    val writer = new StringWriter
    mustache.execute(writer, dataMap)
    writer.flush()
    writer.toString
  }
}

object CalendarEventReplyMustacheFactory {
  val MUSTACHE_FACTORY: MustacheFactory = new CalendarEventReplyMustacheFactory
}

// To avoid HTML escaping
private class CalendarEventReplyMustacheFactory extends DefaultMustacheFactory {

  override def encode(value: String, writer: java.io.Writer): Unit =
    Try(writer.append(value)) match {
      case Success(_) => ()
      case Failure(e) => throw new RuntimeException("Encode failed", e)
    }
}
