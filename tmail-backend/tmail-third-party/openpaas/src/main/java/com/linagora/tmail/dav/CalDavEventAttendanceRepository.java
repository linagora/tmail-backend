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
import static com.linagora.tmail.james.jmap.model.CalendarEventReplyResults.ReplyResults;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.mail.BlobId;
import org.apache.james.jmap.routes.BlobNotFoundException;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageId;
import org.reactivestreams.Publisher;

import com.linagora.tmail.dav.cal.FreeBusyRequest;
import com.linagora.tmail.dav.cal.FreeBusyResponse;
import com.linagora.tmail.james.jmap.AttendanceStatus;
import com.linagora.tmail.james.jmap.EventAttendanceRepository;
import com.linagora.tmail.james.jmap.MessagePartBlobId;
import com.linagora.tmail.james.jmap.calendar.BlobCalendarResolver;
import com.linagora.tmail.james.jmap.model.CalendarEventAttendanceResults;
import com.linagora.tmail.james.jmap.model.CalendarEventParsed;
import com.linagora.tmail.james.jmap.model.CalendarEventReplyResults;
import com.linagora.tmail.james.jmap.model.EventAttendanceStatusEntry;
import com.linagora.tmail.james.jmap.model.LanguageLocation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;

public class CalDavEventAttendanceRepository implements EventAttendanceRepository {

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
    private final MessageId.Factory messageIdFactory;
    private final MessageIdManager messageIdManager;
    private final DavUserProvider davUserProvider;
    private final BlobCalendarResolver blobCalendarResolver;

    @Inject
    public CalDavEventAttendanceRepository(DavClient davClient,
                                           SessionProvider sessionProvider, MessageId.Factory messageIdFactory,
                                           MessageIdManager messageIdManager,
                                           DavUserProvider davUserProvider,
                                           BlobCalendarResolver blobCalendarResolver) {
        this.davClient = davClient;
        this.sessionProvider = sessionProvider;
        this.messageIdFactory = messageIdFactory;
        this.messageIdManager = messageIdManager;
        this.davUserProvider = davUserProvider;
        this.blobCalendarResolver = blobCalendarResolver;
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
            .map(DavCalendarObject::parse)
            .flatMap(calendarEventParsed ->
                Mono.justOrEmpty(OptionConverters.toJava(calendarEventParsed.getAttendanceStatus(davUser.username())))
                    .flatMap(attendanceStatus -> freeBusyQuery(davUser, calendarEventParsed)
                        .map(freeBusyStatus -> EventAttendanceStatusEntry.of(blobId, attendanceStatus, FreeBusyStatus.FREE.equals(freeBusyStatus)))))
            .map(AttendanceResult()::done)
            .switchIfEmpty(Mono.just(AttendanceResult().notFound(blobId)))
            .onErrorResume(error -> Mono.just(AttendanceResult().notDone(blobId, error)));
    }

    private Mono<DavCalendarObject> fetchCalendarObject(DavUser davUser, BlobId blobId) {
        return Mono.fromCallable(() -> MessagePartBlobId.tryParse(messageIdFactory, blobId.value().toString()).get())
            .flatMap(messagePartBlobId -> getCalendarEventUidByBlobId(davUser, messagePartBlobId))
            .flatMap(eventUid -> davClient.getCalendarObject(davUser, eventUid)
                .switchIfEmpty(Mono.error(() -> new RuntimeException("Unable to find any calendar objects containing VEVENT with id '%s'".formatted(eventUid)))));
    }

    private Mono<EventUid> getCalendarEventUidByBlobId(DavUser davUser, MessagePartBlobId messagePartBlobId) {
        return Mono.from(messageIdManager.getMessagesReactive(List.of(messagePartBlobId.getMessageId()),
                FetchGroup.HEADERS, sessionProvider.createSystemSession(Username.of(davUser.username()))))
            .map(EventUid::fromMessageHeaders);
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
    public Publisher<CalendarEventReplyResults> setAttendanceStatus(Username username, AttendanceStatus attendanceStatus,
                                                                    List<BlobId> eventBlobIds, Optional<LanguageLocation> language) {
        return davUserProvider.provide(username)
            .flatMapMany(davUser -> Flux.fromIterable(eventBlobIds)
                .flatMap(blobId -> setAttendanceStatus(davUser, blobId, attendanceStatus), DEFAULT_CONCURRENCY))
            .reduce(CalendarEventReplyResults::merge);
    }

    private Mono<CalendarEventReplyResults> setAttendanceStatus(DavUser davUser, BlobId blobId, AttendanceStatus attendanceStatus) {
        MailboxSession session = sessionProvider.createSystemSession(Username.of(davUser.username()));
        UnaryOperator<DavCalendarObject> calendarTransformation = calendarObject -> calendarObject.withPartStat(davUser.username(), attendanceStatus.toPartStat());
        return blobCalendarResolver.resolveRequestCalendar(blobId, session).asJava()
            .flatMap(calendar -> fetchCalendarObject(davUser, blobId)
                .flatMap(calendarObject -> davClient.updateCalendarObject(davUser, calendarObject.uri(), calendarTransformation))
                .thenReturn(ReplyResults().done(blobId))
                .onErrorResume(e -> Mono.just(ReplyResults().notDone(blobId, e, davUser.username()))))
            .onErrorResume((e ->
                switch (e) {
                    case BlobNotFoundException b -> Mono.just(ReplyResults().notFound(blobId));
                    default -> Mono.just(ReplyResults().notDone(blobId, e, davUser.username()));
            }));
    }
}