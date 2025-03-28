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

import com.linagora.tmail.james.jmap.calendar.CalendarEventModifier.ImplicitCalendar
import com.linagora.tmail.james.jmap.calendar._
import com.linagora.tmail.james.jmap.model._
import com.linagora.tmail.james.jmap.{CalendarEventNotFoundException, CalendarEventRepository}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.property.immutable.ImmutableMethod
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.{SetErrorDescription, invalidArgumentValue}
import org.apache.james.jmap.mail.{BlobId, BlobIds}
import org.apache.james.jmap.routes.BlobNotFoundException
import org.apache.james.mailbox.MailboxSession
import org.slf4j.{Logger, LoggerFactory}
import reactor.core.scala.publisher.{SFlux, SMono}

object EventCounterAcceptedResults {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[EventCounterAcceptedResults])

  def merge(r1: EventCounterAcceptedResults, r2: EventCounterAcceptedResults): EventCounterAcceptedResults =
    EventCounterAcceptedResults(
      done = BlobIds(r1.done.value ++ r2.done.value),
      notFound = (r1.notFound ++ r2.notFound).reduceOption((notFound1, notFound2) => notFound1.merge(notFound2)),
      notDone = (r1.notDone ++ r2.notDone).reduceOption((notDone1, notDOne2) => notDone1.merge(notDOne2)))

  def done(blobId: BlobId): EventCounterAcceptedResults =
    EventCounterAcceptedResults(done = BlobIds(Seq(blobId.value)))

  def notFound(blobId: BlobId): EventCounterAcceptedResults = EventCounterAcceptedResults(notFound = Some(CalendarEventNotFound(Set(blobId.value))))

  def notDone(blobId: BlobId, throwable: Throwable, username: String): EventCounterAcceptedResults =
    EventCounterAcceptedResults(notDone = Some(CalendarEventNotDone(Map(blobId.value -> asSetError(throwable, username)))))

  def notDone(notParsable: CalendarEventNotParsable): EventCounterAcceptedResults =
    notParsable.value match {
      case set if set.isEmpty => EventCounterAcceptedResults()
      case _ => EventCounterAcceptedResults(notDone = Some(CalendarEventNotDone(notParsable.asSetErrorMap)))
    }

  private def asSetError(throwable: Throwable, username: String): SetError = {
    LOGGER.info("Error when accept counter event for {}: {}", username, throwable.getMessage)
    throwable match {
      case _: BlobNotFoundException => SetError.notFound(SetErrorDescription(throwable.getMessage))
      case _: InvalidCalendarFileException => SetError(invalidArgumentValue, SetErrorDescription("The calendar file is not valid"), None)
      case _: CalendarEventNotFoundException => SetError("eventNotFound", SetErrorDescription("The event you counter is not yet on your calendar"), None)
      case _: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(throwable.getMessage))
      case _ =>
        LOGGER.error("serverFail to accept counter event for {}", username, throwable)
        SetError.serverFail(SetErrorDescription(throwable.getMessage))
    }
  }
}

case class EventCounterAcceptedResults(done: BlobIds = BlobIds(Seq.empty),
                                       notFound: Option[CalendarEventNotFound] = None,
                                       notDone: Option[CalendarEventNotDone] = None)

class CalendarEventCounterPerformer @Inject()(calendarEventRepository: CalendarEventRepository,
                                              calendarResolver: CalendarResolver) {

  def accept(mailboxSession: MailboxSession, blobIds: Seq[BlobId]): SMono[EventCounterAcceptedResults] =
    SFlux.fromIterable(blobIds)
      .flatMap(blobId => accept(mailboxSession, blobId))
      .reduce(EventCounterAcceptedResults.merge)

  private def accept(mailboxSession: MailboxSession, blobId: BlobId): SMono[EventCounterAcceptedResults] = {
    calendarResolver.resolveRequestCalendar(blobId, mailboxSession, Some(ImmutableMethod.COUNTER))
      .flatMap(counterCalendar => {
        SMono(calendarEventRepository.updateEvent(mailboxSession.getUser, getEventUid(counterCalendar), CalendarEventModifier.of(counterCalendar, mailboxSession.getUser)))
          .`then`(SMono.just(EventCounterAcceptedResults.done(blobId)))
      })
      .switchIfEmpty(SMono.just(EventCounterAcceptedResults.notFound(blobId)))
      .onErrorResume({
        case _: BlobNotFoundException => SMono.just(EventCounterAcceptedResults.notFound(blobId))
        case error => SMono.just(EventCounterAcceptedResults.notDone(blobId, error, mailboxSession.getUser.asString()))
      })
  }

  private def getEventUid(counterCalendar: Calendar): String =
    CalendarUidField.from(counterCalendar.getFirstVEvent).map(_.value) match {
      case Some(uid) => uid
      case None => throw new IllegalArgumentException("The calendar file must contain a UID property")
    }

}
