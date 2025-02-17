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

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.core.Id;
import org.apache.james.jmap.mail.BlobId;
import org.apache.james.jmap.mail.BlobIds;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageId;
import org.reactivestreams.Publisher;

import com.linagora.tmail.james.jmap.AttendanceStatus;
import com.linagora.tmail.james.jmap.EventAttendanceRepository;
import com.linagora.tmail.james.jmap.MessagePartBlobId;
import com.linagora.tmail.james.jmap.model.CalendarEventAttendanceResults;
import com.linagora.tmail.james.jmap.model.CalendarEventAttendanceResults$;
import com.linagora.tmail.james.jmap.model.CalendarEventReplyResults;
import com.linagora.tmail.james.jmap.model.CalendarEventReplyResults$;
import com.linagora.tmail.james.jmap.model.EventAttendanceStatusEntry;
import com.linagora.tmail.james.jmap.model.LanguageLocation;

import eu.timepit.refined.api.Refined;
import net.fortuna.ical4j.model.parameter.PartStat;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.CollectionConverters;

public class CalDavEventAttendanceRepository implements EventAttendanceRepository {
    private static Flux<Refined<String, Id.IdConstraint>> asFlux(BlobIds blobIds) {
        return Flux.fromIterable(CollectionConverters.SeqHasAsJava(blobIds.value()).asJava());
    }

    private static BlobId asBlobId(Refined<String, Id.IdConstraint> blobId) {
        try {
            return BlobId.of(blobId.value()).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert Refined Id blobId '%s' to BlobId object".formatted(blobId), e);
        }
    }

    private final DavClient davClient;
    private final SessionProvider sessionProvider;
    private final MessageId.Factory messageIdFactory;
    private final MessageIdManager messageIdManager;
    private final DavUserProvider davUserProvider;

    @Inject
    public CalDavEventAttendanceRepository(DavClient davClient,
                                           SessionProvider sessionProvider, MessageId.Factory messageIdFactory,
                                           MessageIdManager messageIdManager,
                                           DavUserProvider davUserProvider) {
        this.davClient = davClient;
        this.sessionProvider = sessionProvider;
        this.messageIdFactory = messageIdFactory;
        this.messageIdManager = messageIdManager;
        this.davUserProvider = davUserProvider;
    }

    @Override
    public Publisher<CalendarEventAttendanceResults> getAttendanceStatus(Username username, BlobIds blobIds) {
        return davUserProvider.provide(username)
            .flatMapMany(davUser -> asFlux(blobIds)
                .map(CalDavEventAttendanceRepository::asBlobId)
                .flatMap(blobId -> getAttendanceStatus(davUser, blobId), DEFAULT_CONCURRENCY))
            .reduce(CalendarEventAttendanceResults$.MODULE$.empty(), CalendarEventAttendanceResults$.MODULE$::merge);
    }

    private Mono<CalendarEventAttendanceResults> getAttendanceStatus(DavUser davUser, BlobId blobId) {
        return fetchCalendarObject(davUser, blobId)
            .map(DavCalendarObject::parse)
            .<AttendanceStatus>handle((event, sink) -> event.getAttendanceStatus(davUser.username())
                .fold(Optional::<AttendanceStatus>empty, Optional::of)
                .ifPresent(sink::next))
            .map(status -> new EventAttendanceStatusEntry(blobId.value().value(),status))
            .map(CalendarEventAttendanceResults$.MODULE$::done)
            .switchIfEmpty(Mono.just(CalendarEventAttendanceResults$.MODULE$.notFound(blobId)))
            .onErrorResume(error -> Mono.just(CalendarEventAttendanceResults$.MODULE$.notDone(blobId, error)));
    }

    private Mono<DavCalendarObject> fetchCalendarObject(DavUser davUser, BlobId blobId) {
        MailboxSession session = sessionProvider.createSystemSession(Username.of(davUser.username()));
        MessageId messageId = MessagePartBlobId.tryParse(messageIdFactory, blobId.value().value()).get().getMessageId();

        return Mono.from(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.HEADERS, session))
            .map(EventUid::fromMessageHeaders)
            .flatMap(eventUid -> davClient.getCalendarObject(davUser, eventUid)
                .switchIfEmpty(Mono.error(() -> new RuntimeException("Unable to find any calendar objects containing VEVENT with id '%s'".formatted(eventUid)))));
    }

    @Override
    public Publisher<CalendarEventReplyResults> setAttendanceStatus(Username username, AttendanceStatus attendanceStatus,
                                                                    BlobIds eventBlobIds, Optional<LanguageLocation> language) {
        return davUserProvider.provide(username)
            .flatMapMany(davUser -> asFlux(eventBlobIds)
                .map(CalDavEventAttendanceRepository::asBlobId)
                .flatMap(blobId -> setAttendanceStatus(davUser, blobId, attendanceStatus), DEFAULT_CONCURRENCY));
    }

    private Mono<CalendarEventReplyResults> setAttendanceStatus(DavUser davUser, BlobId blobId, AttendanceStatus attendanceStatus) {
        PartStat partStat = attendanceStatus.toPartStat().orElseThrow();
        UnaryOperator<DavCalendarObject> calendarTransformation = calendarObject -> calendarObject.withPartStat(davUser.username(), partStat);
        return fetchCalendarObject(davUser, blobId)
            .flatMap(calendarObject -> davClient.updateCalendarObject(davUser, calendarObject.uri(), calendarTransformation))
            .thenReturn(CalendarEventReplyResults$.MODULE$.done(blobId))
            .onErrorResume(e -> Mono.just(CalendarEventReplyResults$.MODULE$.notDone(blobId, e, davUser.username())));
    }
}