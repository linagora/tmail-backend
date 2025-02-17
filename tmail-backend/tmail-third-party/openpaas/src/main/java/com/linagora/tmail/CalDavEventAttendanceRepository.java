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

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.internet.AddressException;

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
import com.linagora.tmail.james.jmap.model.CalendarAttendeeParticipationStatus;
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
import net.fortuna.ical4j.model.property.Attendee;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.collection.JavaConverters;
import scala.jdk.CollectionConverters;

public class CalDavEventAttendanceRepository implements EventAttendanceRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavEventAttendanceRepository.class);
    private static final String X_MEETING_UID_HEADER = "X-MEETING-UID";
    private static final int MAILTO_PREFIX_LENGTH = "MAILTO:".length();

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
    public Publisher<CalendarEventAttendanceResults> getAttendanceStatus(Username username,
                                                                         BlobIds calendarEventBlobIds) {
        MailboxSession session = sessionProvider.createSystemSession(username);

        return toDavUser(username).flatMapMany(davUser -> blobIdsAsFlux(calendarEventBlobIds)
                    .flatMap(blobId -> getAttendanceStatusFromBlob(username, davUser, blobIdFromRefinedId(blobId), session), ReactorUtils.DEFAULT_CONCURRENCY)
                    .reduce(CalendarEventAttendanceResults$.MODULE$.empty(), CalendarEventAttendanceResults$.MODULE$::merge));
    }

    private Mono<CalendarEventAttendanceResults> getAttendanceStatusFromBlob(Username username, DavUser davUser,
                                                                             BlobId blobId,
                                                                             MailboxSession session) {
        return fetchCalendarObject(blobId, davUser, session)
            .map(calendarObject -> getAttendanceStatusFromCalendarObject(blobId, calendarObject, username, session))
            .switchIfEmpty(Mono.just(CalendarEventAttendanceResults$.MODULE$.notFound(blobId)))
            .onErrorResume(error -> Mono.just(CalendarEventAttendanceResults$.MODULE$.notDone(blobId, error, session)));
    }

    private CalendarEventAttendanceResults getAttendanceStatusFromCalendarObject(BlobId blobId,
                                                                                 DavCalendarObject calendarObject, Username username, MailboxSession mailboxSession) {

        try {
            List<CalendarEventParsed> events = JavaConverters.asJava(CalendarEventParsed.from(calendarObject.calendarData()));

            if (events.isEmpty()) {
                LOGGER.debug("No VEvents found in calendar object. Returning empty attendance results.");
                return CalendarEventAttendanceResults.empty();
            } else if (events.size() != 1) {
                LOGGER.debug("Expected exactly one VEvent, but found {} entries. Using the first VEvent. " +
                    "This may indicate unhandled recurrent events or a malformed calendar object. VEvents: {}",
                    events.size(), events);
            }

            return getAttendanceStatusFromCalendarEvent(blobId, username, events.getFirst());

        } catch (Exception e) {
            LOGGER.debug("Error parsing calendar data for blobId '{}'", blobId.value(), e);
            return CalendarEventAttendanceResults$.MODULE$.notDone(blobId, e, mailboxSession);
        }
    }

    private CalendarEventAttendanceResults getAttendanceStatusFromCalendarEvent(BlobId blobId, Username username, CalendarEventParsed event) throws AddressException {
        return event.participants()
            .findParticipantByMailTo(username.asMailAddress().toString())
            .flatMap(CalendarAttendeeField::participationStatus)
            .map(status -> CalendarEventAttendanceResults$.MODULE$.done(createEventAttendanceStatusEntry(blobId, status)))
            .getOrElse(() -> CalendarEventAttendanceResults$.MODULE$.notFound(blobId));
    }

    private static EventAttendanceStatusEntry createEventAttendanceStatusEntry(BlobId blobId, CalendarAttendeeParticipationStatus attendanceStatus) {
        return new EventAttendanceStatusEntry(blobIdToString(blobId), AttendanceStatus.fromCalendarAttendeeParticipationStatus(attendanceStatus).orElseThrow());
    }

    private Mono<DavUser> toDavUser(Username username) {
        try {
            return davUserProvider.provide(username.asMailAddress())
                .switchIfEmpty(Mono.error(() -> new RuntimeException("Unable to find user in Dav server with username '%s'".formatted(username.asString()))));
        } catch (AddressException e) {
            LOGGER.debug("Failed to get user DAV information", e);
            return Mono.empty();
        }
    }

    @Override
    public Publisher<CalendarEventReplyResults> setAttendanceStatus(Username username,
                                                                    AttendanceStatus attendanceStatus,
                                                                    BlobIds eventBlobIds,
                                                                    Optional<LanguageLocation> maybePreferredLanguage) {
        MailboxSession session = sessionProvider.createSystemSession(username);

        return toDavUser(username).flatMapMany(davUser -> blobIdsAsFlux(eventBlobIds)
                    .flatMap(blobId -> updateAttendanceStatusForEventBlob(blobIdFromRefinedId(blobId), attendanceStatus, davUser, session)));
    }

    private Mono<CalendarEventReplyResults> updateAttendanceStatusForEventBlob(BlobId blobId, AttendanceStatus attendanceStatus, DavUser davUser, MailboxSession session) {
        PartStat partStat = attendanceStatus.toPartStat().orElseThrow();
        return fetchCalendarObject(blobId, davUser, session)
            .flatMap(targetCalendarObject -> updateCalendarObject(davUser, targetCalendarObject.uri(), partStat))
            .thenReturn(CalendarEventReplyResults$.MODULE$.done(blobId))
            .onErrorResume(e -> Mono.just(CalendarEventReplyResults$.MODULE$.notDone(blobId, e, session)));
    }

    private Mono<Void> updateCalendarObject(DavUser davUser, URI calendarObjectURI, PartStat partStat) {
        return davClient.updateCalendarObject(davUser, calendarObjectURI, calendarObject -> createUpdatedCalendar(calendarObject, davUser.username(), partStat));
    }

    private Mono<DavCalendarObject> doGetCalendarObjectContainingVEvent(DavUser davUser, String eventUid) {
        return davClient.getCalendarObjectContainingVEvent(davUser, eventUid)
            .switchIfEmpty(Mono.error(() -> new RuntimeException("Unable to find any calendar objects containing VEVENT with id '%s'".formatted(eventUid))));
    }

    private DavCalendarObject createUpdatedCalendar(DavCalendarObject calendarObject, String targetAttendeeEmail, PartStat partStat) {
        Calendar updatedCalendarData = calendarObject.calendarData().copy();
        LOGGER.trace("Calendar to update: {}", calendarObject.calendarData());

        List<VEvent> updatedVEvents = updatedCalendarData.<VEvent>getComponents(Component.VEVENT)
            .stream().map(vEvent ->
                vEvent.getAttendees()
                    .stream()
                    .filter(attendee -> targetAttendeeEmail.equals(attendee.getCalAddress().toASCIIString().substring(MAILTO_PREFIX_LENGTH)))
                    .findAny()
                    .map(attendee -> createUpdatedVEvent(partStat, vEvent, attendee))
                    .orElse(vEvent))
            .toList();

        updatedCalendarData.setComponentList(new ComponentList<>(updatedVEvents));

        LOGGER.trace("Calendar updated: {}", updatedCalendarData);

        return new DavCalendarObject(calendarObject.uri(), updatedCalendarData, calendarObject.eTag());
    }

    private static VEvent createUpdatedVEvent(PartStat partStat, VEvent vEvent, Attendee attendee) {
        return (VEvent) vEvent.with(RelationshipPropertyModifiers.ATTENDEE, attendee.replace(partStat));
    }

    private String retrieveEventUid(MessageResult messageResult) {
        return findHeaderByName(messageResult, X_MEETING_UID_HEADER)
            .map(Header::getValue)
            .orElseThrow(() -> new RuntimeException("Unable to retrieve X_MEETING_UID_HEADER (VEVENT uid) from message with id '%s'".formatted(messageResult.getMessageId().serialize())));
    }

    private Optional<Header> findHeaderByName(MessageResult messageResult, String targetHeaderName) {
        try {
            Iterator<Header> headers = messageResult.getHeaders().headers();
            return Iterators.tryFind(headers, header -> header.getName().equals(targetHeaderName)).toJavaUtil();
        } catch (Exception e) {
            LOGGER.debug("Failed to find header '{}' in message '{}'", targetHeaderName, messageResult.getMessageId().serialize(), e);
            return Optional.empty();
        }
    }

    private Mono<DavCalendarObject> fetchCalendarObject(BlobId blobId, DavUser davUser, MailboxSession session) {
        return extractMessageId(blobIdToString(blobId))
            .flatMap(messageId ->
                Mono.from(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.HEADERS, session))
                    .map(this::retrieveEventUid)
                    .flatMap(eventUid -> doGetCalendarObjectContainingVEvent(davUser, eventUid)));
    }

    private Mono<MessageId> extractMessageId(String blobId) {
        return Mono.fromCallable(() -> MessagePartBlobId.tryParse(blobId)
                .map(MessagePartBlobId::getMessageId)
                .map(messageIdFactory::fromString)
                .get());
    }

    private static BlobId blobIdFromString(String blobId) {
        try {
            return BlobId.of(blobId).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert string blobId '%s' to BlobId object".formatted(blobId), e);
        }
    }

    private static BlobId blobIdFromRefinedId(Refined<String, Id.IdConstraint> blobId) {
        try {
            return BlobId.of(blobId.value()).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert Refined Id blobId '%s' to BlobId object".formatted(blobId), e);
        }
    }

    private static String blobIdToString(BlobId blobId) {
        Preconditions.checkNotNull(blobId);
        return blobId.value().toString();
    }

    private static Flux<Refined<String, Id.IdConstraint>> blobIdsAsFlux(BlobIds blobIds) {
        return Flux.fromIterable(CollectionConverters.SeqHasAsJava(blobIds.value()).asJava());
    }
}