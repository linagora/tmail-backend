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

package com.linagora.tmail.james.jmap.model;

import com.linagora.tmail.james.jmap.AttendanceStatus
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.mail.BlobId
import org.apache.james.mailbox.MailboxSession

object CalendarEventAttendanceResults {
  def merge(r1: CalendarEventAttendanceResults, r2: CalendarEventAttendanceResults): CalendarEventAttendanceResults =
    CalendarEventAttendanceResults(
      done = r1.done ++ r2.done,
      notFound = r1.notFound.orElse(r2.notFound),
      notDone = r1.notDone.orElse(r2.notDone))

  def notFound(blobId: BlobId): CalendarEventAttendanceResults = CalendarEventAttendanceResults(notFound = Some(CalendarEventNotFound(Set(blobId.value))))

  def empty: CalendarEventAttendanceResults = CalendarEventAttendanceResults()

  def done(eventAttendanceEntry: EventAttendanceStatusEntry): CalendarEventAttendanceResults =
    CalendarEventAttendanceResults(List(eventAttendanceEntry))

  def notDone(notParsable: CalendarEventNotParsable): CalendarEventAttendanceResults =
    CalendarEventAttendanceResults(notDone = Some(CalendarEventNotDone(notParsable.asSetErrorMap)))

  def notDone(blobId: BlobId, throwable: Throwable): CalendarEventAttendanceResults =
    CalendarEventAttendanceResults(notDone = Some(CalendarEventNotDone(Map(blobId.value -> asSetError(throwable)))), done = List(), notFound = None)

  private def asSetError(throwable: Throwable): SetError = throwable match {
    case _: InvalidCalendarFileException | _: IllegalArgumentException =>
      SetError.invalidPatch(SetErrorDescription(throwable.getMessage))
    case _ =>
      SetError.serverFail(SetErrorDescription(throwable.getMessage))
  }
}

case class CalendarEventAttendanceResults(done: List[EventAttendanceStatusEntry] = List(),
                                          notFound: Option[CalendarEventNotFound] = Option.empty,
                                          notDone: Option[CalendarEventNotDone] = Option.empty)

case class EventAttendanceStatusEntry(blobId: String, eventAttendanceStatus:AttendanceStatus)