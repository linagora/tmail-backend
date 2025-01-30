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

package com.linagora.tmail.james.jmap.model

import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.mail.{BlobId, BlobIds}
import org.apache.james.mailbox.MailboxSession
import org.slf4j.{Logger, LoggerFactory}

object CalendarEventReplyResults {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[CalendarEventReplyResults])

  def merge(r1: CalendarEventReplyResults, r2: CalendarEventReplyResults): CalendarEventReplyResults =
    CalendarEventReplyResults(
      done = BlobIds(r1.done.value ++ r2.done.value),
      notFound = (r1.notFound ++ r2.notFound).reduceOption((notFound1, notFound2) => notFound1.merge(notFound2)),
      notDone = (r1.notDone ++ r2.notDone).reduceOption((notDone1, notDOne2) => notDone1.merge(notDOne2)))

  def notFound(blobId: BlobId): CalendarEventReplyResults = CalendarEventReplyResults(notFound = Some(CalendarEventNotFound(Set(blobId.value))))

  def empty: CalendarEventReplyResults = CalendarEventReplyResults()

  def notDone(notParsable: CalendarEventNotParsable): CalendarEventReplyResults =
    CalendarEventReplyResults(notDone = Some(CalendarEventNotDone(notParsable.asSetErrorMap)))

  def notDone(blobId: BlobId, throwable: Throwable, mailboxSession: MailboxSession): CalendarEventReplyResults =
    CalendarEventReplyResults(notDone = Some(CalendarEventNotDone(Map(blobId.value -> asSetError(throwable, mailboxSession)))))

  private def asSetError(throwable: Throwable, mailboxSession: MailboxSession): SetError = throwable match {
    case _: InvalidCalendarFileException | _: IllegalArgumentException =>
      LOGGER.info("Error when generate reply mail for {}: {}", mailboxSession.getUser.asString(), throwable.getMessage)
      SetError.invalidPatch(SetErrorDescription(throwable.getMessage))
    case _ =>
      LOGGER.error("serverFail to generate reply mail for {}", mailboxSession.getUser.asString(), throwable)
      SetError.serverFail(SetErrorDescription(throwable.getMessage))
  }
}

case class CalendarEventReplyResults(done: BlobIds = BlobIds(Seq.empty),
                                     notFound: Option[CalendarEventNotFound] = None,
                                     notDone: Option[CalendarEventNotDone] = None)