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

import java.net.URI;
import java.time.temporal.Temporal;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linagora.tmail.dav.cal.FreeBusyRequest;
import com.linagora.tmail.dav.cal.FreeBusyResponse;
import com.linagora.tmail.james.jmap.AttendanceStatus;
import com.linagora.tmail.james.jmap.CalendarEventCancelledException;
import com.linagora.tmail.james.jmap.CalendarEventNotFoundException;
import com.linagora.tmail.james.jmap.CalendarEventRepository;
import com.linagora.tmail.james.jmap.calendar.CalendarEventModifier;
import com.linagora.tmail.james.jmap.calendar.CalendarResolver;
import com.linagora.tmail.james.jmap.model.CalendarEventAttendanceResults;
import com.linagora.tmail.james.jmap.model.CalendarEventParsed;
import com.linagora.tmail.james.jmap.model.CalendarUidField;
import com.linagora.tmail.james.jmap.model.EventAttendanceStatusEntry;
import com.linagora.tmail.james.jmap.model.RecurrenceIdField;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.RecurrenceId;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.immutable.ImmutableMethod;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;

public class CalDavEventRepository implements CalendarEventRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavEventRepository.class);

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
                Mono.justOrEmpty(OptionConverters.toJava(calendarEventParsed.getAttendanceStatus(davUser.username().asString())))
                    .flatMap(attendanceStatus -> freeBusyQuery(davUser, calendarEventParsed)
                        .map(freeBusyStatus -> EventAttendanceStatusEntry.of(blobId, attendanceStatus, FreeBusyStatus.FREE.equals(freeBusyStatus)))))
            .map(AttendanceResult()::done)
            .switchIfEmpty(Mono.just(AttendanceResult().notFound(blobId)))
            .onErrorResume(error -> Mono.just(AttendanceResult().notDone(blobId, error)));
    }

    private Mono<CalendarEventParsed> fetchCalendarObject(DavUser davUser, BlobId blobId) {
        MailboxSession session = sessionProvider.createSystemSession(davUser.username());

        return Mono.from(calendarResolver.resolveRequestCalendar(blobId, session, OptionConverters.toScala(Optional.empty())))
            .flatMap(requestCalendar -> {
                DavUid eventUid = DavUid.fromCalendarUidField(CalendarUidField.getEventUidFromCalendar(requestCalendar));
                Optional<String> recurrenceId = RecurrenceIdField.getRecurrenceIdAsString(requestCalendar);
                return davClient.caldav(davUser.username()).getCalendarObject(davUser, eventUid)
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
            .flatMap(request -> davClient.caldav(davUser.username()).freeBusyQuery(davUser, request))
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
                CalendarUidField eventUid = CalendarUidField.getEventUidFromCalendar(calendar);
                CalendarEventModifier eventModifier = CalendarEventModifier.withPartStat(username.asString(), attendanceStatus.toPartStat(), calendar);
                return updateEvent(username, eventUid, eventModifier)
                    .onErrorResume(CalendarEventNotFoundException.class, notFound ->
                        importEventViaITIP(username, calendar)
                            .then(updateEvent(username, eventUid, eventModifier)));
            });
    }

    private Mono<Void> importEventViaITIP(Username username, Calendar calendar) {
        return davUserProvider.provide(username)
            .flatMap(davUser -> {
                try {
                    VEvent vevent = (VEvent) calendar.getComponent(Component.VEVENT)
                        .orElseThrow(() -> new RuntimeException("No VEVENT found in calendar"));

                    String organizerEmail = Optional.ofNullable(vevent.getOrganizer())
                        .map(Organizer::getCalAddress)
                        .map(uri -> uri.getSchemeSpecificPart().isEmpty() ? uri.toString() : uri.getSchemeSpecificPart())
                        .orElse(username.asString());

                    ObjectMapper mapper = new ObjectMapper();
                    ObjectNode node = mapper.createObjectNode();
                    node.put("ical", calendar.toString());
                    node.put("sender", organizerEmail);
                    node.put("recipient", username.asString());
                    node.put("replyTo", organizerEmail);
                    vevent.getUid().ifPresent(uid -> node.put("uid", uid.getValue()));
                    vevent.getDateStamp().ifPresent(ds -> node.put("dtstamp", ds.getValue()));
                    calendar.getProperty(Property.METHOD).ifPresent(m -> node.put("method", m.getValue()));
                    Optional.ofNullable(vevent.getSequence()).ifPresent(seq -> node.put("sequence", seq.getValue()));
                    Optional.ofNullable(vevent.getRecurrenceId()).ifPresent(rid -> node.put("recurrence-id", rid.getValue()));

                    return davClient.caldav(username).sendITIPRequest(
                        URI.create(CalDavClient.CALENDAR_PATH + davUser.userId().value()),
                        mapper.writeValueAsBytes(node));
                } catch (Exception e) {
                    return Mono.error(e);
                }
            });
    }

    @Override
    public Mono<Void> updateEvent(Username username, CalendarUidField eventUid, CalendarEventModifier eventModifier) {
        UnaryOperator<DavCalendarObject> updateEventOperator = calendarObject -> calendarObject.withUpdatePatches(eventModifier);
        return davUserProvider.provide(username)
            .flatMap(davUser -> davClient.caldav(davUser.username()).getCalendarObjects(davUser, DavUid.fromCalendarUidField(eventUid))
                .switchIfEmpty(Flux.error(new CalendarEventNotFoundException(username, eventUid)))
                .flatMap(calendarObject -> doUpdateCalendarObject(davUser, calendarObject, username, eventUid, updateEventOperator, eventModifier))
                .next()
                .switchIfEmpty(Mono.error(new CalendarEventNotFoundException(username, eventUid)))
                .then());
    }

    private Mono<Boolean> doUpdateCalendarObject(DavUser davUser, DavCalendarObject calendarObject,
                                                  Username username, CalendarUidField eventUid,
                                                  UnaryOperator<DavCalendarObject> updateEventOperator,
                                                  CalendarEventModifier eventModifier) {
        // Cancellation is checked after the write: PermissionDenied is the only reliable delegated-calendar guard.
        // Writing PARTSTAT on a cancelled event is acceptable (invisible to the user); the error below prevents a false success.
        return davClient.caldav(davUser.username()).updateCalendarObject(calendarObject.uri(), updateEventOperator)
            .thenReturn(Boolean.TRUE)
            .onErrorResume(DavClientException.PermissionDenied.class, e -> {
                LOGGER.debug("Skipping calendar object '{}': permission denied, likely a delegated calendar", calendarObject.uri());
                return Mono.empty();
            })
            .flatMap(updated -> {
                if (isCancelled(calendarObject, eventModifier)) {
                    return Mono.<Boolean>error(new CalendarEventCancelledException(username, eventUid));
                }
                return Mono.just(Boolean.TRUE);
            });
    }

    private boolean isCancelled(DavCalendarObject calendarObject, CalendarEventModifier eventModifier) {
        Optional<RecurrenceId<Temporal>> recurrenceId = scala.jdk.javaapi.OptionConverters.toJava(eventModifier.recurrenceId());
        return calendarObject.calendarData().getComponents(Component.VEVENT).stream()
            .filter(c -> c instanceof VEvent)
            .map(c -> (VEvent) c)
            .filter(vEvent -> recurrenceId
                .map(rid -> rid.equals(vEvent.getRecurrenceId()))
                .orElseGet(() -> vEvent.getRecurrenceId() == null))
            .findFirst()
            .map(VEvent::getStatus)
            .map(status -> Status.VALUE_CANCELLED.equals(status.getValue()))
            .orElse(false);
    }

}