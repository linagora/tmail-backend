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

package com.linagora.tmail;

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.net.URI;
import java.util.Iterator;
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
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.linagora.tmail.dav.DavCalendarObject;
import com.linagora.tmail.dav.DavClient;
import com.linagora.tmail.dav.DavUser;
import com.linagora.tmail.dav.DavUserProvider;
import com.linagora.tmail.james.jmap.AttendanceStatus;
import com.linagora.tmail.james.jmap.EventAttendanceRepository;
import com.linagora.tmail.james.jmap.MessagePartBlobId;
import com.linagora.tmail.james.jmap.model.CalendarAttendeeField;
import com.linagora.tmail.james.jmap.model.CalendarEventAttendanceResults;
import com.linagora.tmail.james.jmap.model.CalendarEventAttendanceResults$;
import com.linagora.tmail.james.jmap.model.CalendarEventParsed;
import com.linagora.tmail.james.jmap.model.CalendarEventReplyResults;
import com.linagora.tmail.james.jmap.model.CalendarEventReplyResults$;
import com.linagora.tmail.james.jmap.model.EventAttendanceStatusEntry;
import com.linagora.tmail.james.jmap.model.LanguageLocation;

import eu.timepit.refined.api.Refined;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.RelationshipPropertyModifiers;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.PartStat;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.collection.JavaConverters;
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

    private static String asBlobId(BlobId blobId) {
        Preconditions.checkNotNull(blobId);
        return blobId.value().toString();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavEventAttendanceRepository.class);
    private static final String X_MEETING_UID_HEADER = "X-MEETING-UID";

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
                .flatMap(blobId -> resolveAttendanceStatus(davUser, blobId), DEFAULT_CONCURRENCY))
            .reduce(CalendarEventAttendanceResults$.MODULE$.empty(), CalendarEventAttendanceResults$.MODULE$::merge);
    }

    private Mono<CalendarEventAttendanceResults> resolveAttendanceStatus(DavUser davUser, BlobId blobId) {
        return fetchCalendarObject(blobId, davUser)
            .map(this::parse)
            .<EventAttendanceStatusEntry>handle((event, sink) -> getAttendanceStatus(blobId, davUser, event).ifPresent(sink::next))
            .map(CalendarEventAttendanceResults$.MODULE$::done)
            .switchIfEmpty(Mono.just(CalendarEventAttendanceResults$.MODULE$.notFound(blobId)))
            .onErrorResume(error -> Mono.just(CalendarEventAttendanceResults$.MODULE$.notDone(blobId, error)));
    }

    private CalendarEventParsed parse(DavCalendarObject calendarObject) {
        List<CalendarEventParsed> events = JavaConverters.asJava(CalendarEventParsed.from(calendarObject.calendarData()));
        if (events.isEmpty()) {
            throw new RuntimeException("No VEvents found in calendar object. Returning empty attendance results.");
        }
        if (events.size() != 1) {
            LOGGER.debug("Expected exactly one VEvent, but found {} entries. Using the first VEvent. " +
                    "This may indicate unhandled recurrent events or a malformed calendar object. VEvents: {}",
                events.size(), events);
        }
        return events.getFirst();
    }

    private Optional<EventAttendanceStatusEntry> getAttendanceStatus(BlobId blobId, DavUser davUser, CalendarEventParsed event) {
        return event.participants()
            .findParticipantByMailTo(davUser.username())
            .flatMap(CalendarAttendeeField::participationStatus)
            .map(status -> AttendanceStatus.fromCalendarAttendeeParticipationStatus(status).orElseThrow())
            .map(status -> new EventAttendanceStatusEntry(asBlobId(blobId),status))
            .fold(Optional::empty, Optional::of);
    }

    @Override
    public Publisher<CalendarEventReplyResults> setAttendanceStatus(Username username,
                                                                    AttendanceStatus attendanceStatus,
                                                                    BlobIds eventBlobIds,
                                                                    Optional<LanguageLocation> maybePreferredLanguage) {
        return davUserProvider.provide(username)
            .flatMapMany(davUser -> asFlux(eventBlobIds)
                .map(CalDavEventAttendanceRepository::asBlobId)
                .flatMap(blobId -> updateAttendanceStatusForEventBlob(blobId, attendanceStatus, davUser), DEFAULT_CONCURRENCY));
    }

    private Mono<CalendarEventReplyResults> updateAttendanceStatusForEventBlob(BlobId blobId, AttendanceStatus attendanceStatus, DavUser davUser) {
        PartStat partStat = attendanceStatus.toPartStat().orElseThrow();
        UnaryOperator<DavCalendarObject> calendarTransformation = updatedCalendarObject(davUser.username(), partStat);
        return fetchCalendarObject(blobId, davUser)
            .flatMap(calendarObject -> davClient.updateCalendarObject(davUser, calendarObject.uri(), calendarTransformation))
            .thenReturn(CalendarEventReplyResults$.MODULE$.done(blobId))
            .onErrorResume(e -> Mono.just(CalendarEventReplyResults$.MODULE$.notDone(blobId, e, davUser.username())));
    }

    private UnaryOperator<DavCalendarObject> updatedCalendarObject(String targetAttendeeEmail, PartStat partStat) {
        return calendarObject -> {
            LOGGER.trace("Calendar to update: {}", calendarObject.calendarData());
            Calendar updatedCalendarData = calendarObject.calendarData().copy();

            updatedCalendarData.setComponentList(new ComponentList<>(updatedCalendarData.<VEvent>getComponents(Component.VEVENT)
                .stream()
                .map(vEvent -> updatedVEvent(targetAttendeeEmail, partStat, vEvent))
                .toList()));

            LOGGER.trace("Calendar updated: {}", updatedCalendarData);

            return new DavCalendarObject(calendarObject.uri(), updatedCalendarData, calendarObject.eTag());
        };
    }

    private static VEvent updatedVEvent(String targetAttendeeEmail, PartStat partStat, VEvent vEvent) {
        return vEvent.getAttendees()
            .stream()
            .filter(attendee -> attendee.getCalAddress().toASCIIString().equalsIgnoreCase("mailto:" + targetAttendeeEmail))
            .findAny()
            .map(attendee -> (VEvent) vEvent.with(RelationshipPropertyModifiers.ATTENDEE, attendee.replace(partStat)))
            .orElse(vEvent);
    }

    private String retrieveEventUid(MessageResult messageResult) {
        try {
            Iterator<Header> headers = messageResult.getHeaders().headers();
            return Iterators.tryFind(headers, header -> header.getName().equals(X_MEETING_UID_HEADER)).toJavaUtil()
                .map(Header::getValue)
                .orElseThrow(() -> new RuntimeException("Unable to retrieve X_MEETING_UID_HEADER (VEVENT uid) from message with id '%s'".formatted(messageResult.getMessageId().serialize())));
        } catch (Exception e) {
            throw new RuntimeException("Failed reading messageResult headers", e);
        }
    }

    private Mono<DavCalendarObject> fetchCalendarObject(BlobId blobId, DavUser davUser) {
        MailboxSession session = sessionProvider.createSystemSession(Username.of(davUser.username()));
        MessageId messageId = MessagePartBlobId.tryParse(messageIdFactory, blobId.value().value()).get().getMessageId();

        return Mono.from(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.HEADERS, session))
            .map(this::retrieveEventUid)
            .flatMap(eventUid -> davClient.getCalendarObject(davUser, eventUid)
                .switchIfEmpty(Mono.error(() -> new RuntimeException("Unable to find any calendar objects containing VEVENT with id '%s'".formatted(eventUid)))));
    }
}