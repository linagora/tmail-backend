/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.calendar.CalendarResolver
import com.linagora.tmail.james.jmap.model.{CalendarEventNotParsable, CalendarEventReplyRequest, CalendarEventReplyResults, LanguageLocation}
import com.linagora.tmail.james.jmap.{AttendanceStatus, EventAttendanceRepository}
import jakarta.inject.{Inject, Named}
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.property.immutable.ImmutableMethod
import org.apache.james.jmap.mail.{BlobId, BlobIds}
import org.apache.james.jmap.routes.BlobNotFoundException
import org.apache.james.mailbox.MailboxSession
import reactor.core.scala.publisher.{SFlux, SMono}

class CalendarEventAttendanceStatusPerformer @Inject()(@Named("calDavSupport") calDavSupport: Boolean,
                                                       val calendarResolver: CalendarResolver,
                                                       val mailReplyPerformer: CalendarEventReplyMailProcessor,
                                                       val eventAttendanceRepository: EventAttendanceRepository) {

  private val isSendMailReply: Boolean = !calDavSupport

  def process(mailboxSession: MailboxSession,
              request: CalendarEventReplyRequest,
              attendanceStatus: AttendanceStatus): SMono[CalendarEventReplyResults] =
    CalendarEventReplyRequest.extractParsedBlobIds(request) match {
      case (notParsable: CalendarEventNotParsable, blobIdList: Seq[BlobId]) =>
        SFlux.fromIterable(blobIdList)
          .flatMap(blobId => handleAttendanceStatusUpdate(mailboxSession, blobId, attendanceStatus, request.language))
          .reduce(CalendarEventReplyResults.empty)(CalendarEventReplyResults.merge)
          .map(result => CalendarEventReplyResults.merge(result, CalendarEventReplyResults.notDone(notParsable)))
    }

  private def handleAttendanceStatusUpdate(mailboxSession: MailboxSession,
                                           blobId: BlobId,
                                           attendanceStatus: AttendanceStatus,
                                           language: Option[LanguageLocation]): SMono[CalendarEventReplyResults] =
    calendarResolver.resolveRequestCalendar(blobId, mailboxSession, Some(ImmutableMethod.REQUEST))
      .flatMap(requestCalendar =>
        SMono(eventAttendanceRepository.setAttendanceStatus(mailboxSession.getUser, attendanceStatus, blobId))
          .`then`(doSendMailReplyIfNecessary(requestCalendar, language, attendanceStatus.toPartStat, mailboxSession))
          .`then`(SMono.just(CalendarEventReplyResults(done = BlobIds(Seq(blobId.value))))))
      .onErrorResume {
        case e: BlobNotFoundException => SMono.just(CalendarEventReplyResults.notFound(e.blobId))
        case e => SMono.just(CalendarEventReplyResults.notDone(blobId, e, mailboxSession.getUser.asString()))
      }

  private def doSendMailReplyIfNecessary(requestCalendar: Calendar,
                                         language: Option[LanguageLocation],
                                         partStat: PartStat,
                                         mailboxSession: MailboxSession): SMono[Unit] =
    if (isSendMailReply) mailReplyPerformer.process(requestCalendar, language, partStat, mailboxSession)
    else SMono.empty
}
