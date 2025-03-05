package com.linagora.tmail.james.jmap.calendar

import com.linagora.tmail.james.jmap.model.{CalendarEventParsed, InvalidCalendarFileException}
import jakarta.inject.Inject
import net.fortuna.ical4j.model.Calendar
import org.apache.james.jmap.mail.BlobId
import org.apache.james.jmap.routes.BlobResolvers
import org.apache.james.mailbox.MailboxSession
import reactor.core.scala.publisher.SMono

import scala.util.Using

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
      Left(new IllegalArgumentException("The calendar file must contain VEVENT component"))
    } else if (Option(calendar.getMethod).map(_.getValue).orNull != "REQUEST") {
      Left(new IllegalArgumentException("The calendar must have REQUEST as a method"))
    } else {
      Right(calendar)
    }
}