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

package com.linagora.tmail.dav;

import static com.linagora.tmail.james.jmap.model.CalendarEventAttendanceResults.AttendanceResult;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.mail.BlobId;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SessionProvider;
import org.reactivestreams.Publisher;

import com.linagora.tmail.dav.cal.FreeBusyRequest;
import com.linagora.tmail.dav.cal.FreeBusyResponse;
import com.linagora.tmail.james.jmap.AttendanceStatus;
import com.linagora.tmail.james.jmap.CalendarEventNotFoundException;
import com.linagora.tmail.james.jmap.CalendarEventRepository;
import com.linagora.tmail.james.jmap.calendar.CalendarEventModifier;
import com.linagora.tmail.james.jmap.calendar.CalendarResolver;
import com.linagora.tmail.james.jmap.model.CalendarEventAttendanceResults;
import com.linagora.tmail.james.jmap.model.CalendarEventParsed;
import com.linagora.tmail.james.jmap.model.CalendarUidField;
import com.linagora.tmail.james.jmap.model.EventAttendanceStatusEntry;
import com.linagora.tmail.james.jmap.model.RecurrenceIdField;

import net.fortuna.ical4j.model.property.immutable.ImmutableMethod;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;

public class CalDavEventRepository implements CalendarEventRepository {

    public enum FreeBusyStatus {
        BUSY,
        FREE;

        static FreeBusyStatus isBusy(boolean isBusy) {
            if (isBusy) {
                return BUSY;
            }
            return FREE;
        }
    }

    private final DavClient davClient;
    private final SessionProvider sessionProvider;
    private final DavUserProvider davUserProvider;
    private final CalendarResolver calendarResolver;

    @Inject
    public CalDavEventRepository(DavClient davClient,
                                 SessionProvider sessionProvider,
                                 DavUserProvider davUserProvider,
                                 CalendarResolver calendarResolver) {
        this.davClient = davClient;
        this.sessionProvider = sessionProvider;
        this.davUserProvider = davUserProvider;
        this.calendarResolver = calendarResolver;
    }

    @Override
    public Publisher<CalendarEventAttendanceResults> getAttendanceStatus(Username username, List<BlobId> blobIds) {
        return davUserProvider.provide(username)
            .flatMapMany(davUser -> Flux.fromIterable(blobIds)
                .flatMap(blobId -> getAttendanceStatus(davUser, blobId), DEFAULT_CONCURRENCY))
            .reduce(CalendarEventAttendanceResults::merge);
    }

    private Mono<CalendarEventAttendanceResults> getAttendanceStatus(DavUser davUser, BlobId blobId) {
        return fetchCalendarObject(davUser, blobId)
            .flatMap(calendarEventParsed ->
                Mono.justOrEmpty(OptionConverters.toJava(calendarEventParsed.getAttendanceStatus(davUser.username())))
                    .flatMap(attendanceStatus -> freeBusyQuery(davUser, calendarEventParsed)
                        .map(freeBusyStatus -> EventAttendanceStatusEntry.of(blobId, attendanceStatus, FreeBusyStatus.FREE.equals(freeBusyStatus)))))
            .map(AttendanceResult()::done)
            .switchIfEmpty(Mono.just(AttendanceResult().notFound(blobId)))
            .onErrorResume(error -> Mono.just(AttendanceResult().notDone(blobId, error)));
    }

    private Mono<CalendarEventParsed> fetchCalendarObject(DavUser davUser, BlobId blobId) {
        MailboxSession session = sessionProvider.createSystemSession(Username.of(davUser.username()));

        return Mono.from(calendarResolver.resolveRequestCalendar(blobId, session, OptionConverters.toScala(Optional.empty())))
            .flatMap(requestCalendar -> {
                String eventUid = CalendarUidField.getEventUidFromCalendar(requestCalendar);
                Optional<String> recurrenceId = RecurrenceIdField.getRecurrenceIdAsString(requestCalendar);
                return davClient.getCalendarObject(davUser, new EventUid(eventUid))
                    .switchIfEmpty(Mono.error(() -> new DavClientException("Unable to find any calendar objects containing VEVENT with id '%s'".formatted(eventUid))))
                    .map(davCalendarObject -> davCalendarObject.parse(recurrenceId));
            });
    }

    public Mono<FreeBusyStatus> freeBusyQuery(DavUser davUser, CalendarEventParsed calendarEventParsed) {
        Function<FreeBusyResponse, Boolean> isBusyFunction = freeBusyResponse -> freeBusyResponse.users()
            .stream().anyMatch(user -> user.calendars()
                .stream().anyMatch(FreeBusyResponse.Calendar::isBusy));

        return Mono.justOrEmpty(FreeBusyRequest.tryFromCalendarEventParsed(calendarEventParsed))
            .map(builder -> builder.user(davUser.userId()).build())
            .flatMap(request -> davClient.freeBusyQuery(davUser, request))
            .map(isBusyFunction)
            .map(FreeBusyStatus::isBusy)
            .defaultIfEmpty(FreeBusyStatus.FREE);
    }

    @Override
    public Mono<Void> setAttendanceStatus(Username username, AttendanceStatus attendanceStatus, BlobId eventBlobId) {
        return davUserProvider.provide(username)
            .flatMap(davUser -> setAttendanceStatus(username, eventBlobId, attendanceStatus, sessionProvider.createSystemSession(username)));
    }

    private Mono<Void> setAttendanceStatus(Username username, BlobId blobId, AttendanceStatus attendanceStatus, MailboxSession session) {
        return calendarResolver.resolveRequestCalendar(blobId, session, OptionConverters.toScala(Optional.of(ImmutableMethod.REQUEST))).asJava()
            .flatMap(calendar -> {
                String eventUid = CalendarUidField.getEventUidFromCalendar(calendar);
                CalendarEventModifier eventModifier = CalendarEventModifier.withPartStat(username.asString(), attendanceStatus.toPartStat(), calendar);
                return updateEvent(username, eventUid, eventModifier);
            });
    }

    @Override
    public Mono<Void> updateEvent(Username username, String eventUid, CalendarEventModifier eventModifier) {
        UnaryOperator<DavCalendarObject> updateEventOperator = calendarObject -> calendarObject.withUpdatePatches(eventModifier);
        return davUserProvider.provide(username)
            .flatMap(davUser -> davClient.getCalendarObject(davUser, new EventUid(eventUid))
                .switchIfEmpty(Mono.error(new CalendarEventNotFoundException(username.asString(), eventUid)))
                .flatMap(calendarObject -> davClient.updateCalendarObject(davUser, calendarObject.uri(), updateEventOperator)));
    }

}