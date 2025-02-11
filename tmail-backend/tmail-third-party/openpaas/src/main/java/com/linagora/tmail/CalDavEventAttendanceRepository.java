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

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.internet.AddressException;

import org.apache.james.core.Username;
import org.apache.james.jmap.mail.BlobId;
import org.apache.james.jmap.mail.BlobIds;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        return toDavUser(username)
            .flatMapMany(davUser ->
                Flux.fromIterable(CollectionConverters.SeqHasAsJava(calendarEventBlobIds.value()).asJava())
                    .flatMap(blobId -> getAttendanceStatusFromBlob(username, davUser, blobId.value(), session))
                    .reduce(CalendarEventAttendanceResults$.MODULE$.empty(), CalendarEventAttendanceResults$.MODULE$::merge))
            .onErrorResume(e -> generateGetAttendanceStatusErrorResponse(e, calendarEventBlobIds, session));
    }

    private Mono<CalendarEventAttendanceResults> generateGetAttendanceStatusErrorResponse(Throwable error, BlobIds calendarEventBlobIds, MailboxSession mailboxSession) {
        return Flux.fromIterable(CollectionConverters.SeqHasAsJava(calendarEventBlobIds.value()).asJava())
            .map(blobId -> BlobId.of(blobId.value()).get())
            .map(blobId -> CalendarEventAttendanceResults$.MODULE$.notDone(blobId, error, mailboxSession))
            .reduce(CalendarEventAttendanceResults$.MODULE$.empty(), CalendarEventAttendanceResults$.MODULE$::merge);
    }

    private Mono<CalendarEventAttendanceResults> getAttendanceStatusFromBlob(Username username, DavUser davUser,
                                                                                      String blobId,
                                                                                      MailboxSession systemMailboxSession) {
        return retrieveEventUid(blobId, systemMailboxSession)
            .flatMap(eventUid -> davClient.getCalendarObjectContainingVEvent(davUser, eventUid))
            .map(calendarObject -> getAttendanceStatusFromCalendarObject(
                BlobId.of(blobId).get(), calendarObject, username, systemMailboxSession))
            .onErrorResume(error -> Mono.just(CalendarEventAttendanceResults$.MODULE$.notDone(BlobId.of(blobId).get(), error, systemMailboxSession)));
    }

    private CalendarEventAttendanceResults getAttendanceStatusFromCalendarObject(BlobId blobId,
        DavCalendarObject calendarObject, Username username, MailboxSession mailboxSession) {

        try {
            List<CalendarEventParsed> events =
                JavaConverters.asJava(CalendarEventParsed.from(calendarObject.calendarData()));

            if (events.isEmpty()) {
                LOGGER.debug("No VEvents found in calendar object. Returning empty attendance results.");
                return CalendarEventAttendanceResults.empty();
            } else if (events.size() != 1) {
                LOGGER.debug(
                    "Expected exactly one VEvent, but found {} entries. Using the first VEvent. " +
                    "This may indicate unhandled recurrent events or a malformed calendar object. VEvents: {}",
                    events.size(), events);
            }

            return getAttendanceStatusFromCalendarEvent(blobId, username, events.getFirst());

        } catch (Exception e) {
            LOGGER.debug("Error parsing calendar data for blobId '{}'", blobId.value(), e);
            return CalendarEventAttendanceResults$.MODULE$.notDone(blobId, e, mailboxSession);
        }
    }

    private static CalendarEventAttendanceResults getAttendanceStatusFromCalendarEvent(BlobId blobId, Username username, CalendarEventParsed event) throws AddressException {
        return event.participants()
            .findParticipantByMailTo(username.asMailAddress().toString())
            .flatMap(CalendarAttendeeField::participationStatus)
            .map(status -> CalendarEventAttendanceResults$.MODULE$.done(
                new EventAttendanceStatusEntry(
                    blobId.value().toString(), AttendanceStatus.fromCalendarAttendeeParticipationStatus(status).orElseThrow())
            )).getOrElse(() -> CalendarEventAttendanceResults$.MODULE$.notFound(blobId));
    }

    private Mono<DavUser> toDavUser(Username username) {
        try {
            return davUserProvider.provide(username.asMailAddress())
                .switchIfEmpty(Mono.error(new RuntimeException("We couldn't find user in Dav server with username '%s'".formatted(username.asString()))));
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

        return toDavUser(username)
            .flatMapMany(davUser ->
                    Flux.fromIterable(CollectionConverters.SeqHasAsJava(eventBlobIds.value()).asJava())
                        .flatMap(blobId -> updateAttendanceStatusForEventBlob(BlobId.of(blobId.value()).get(), attendanceStatus, davUser, session)))
            .onErrorResume(e -> generateSetAttendanceStatusErrorResponse(e, eventBlobIds, session));
    }

    private Mono<CalendarEventReplyResults> generateSetAttendanceStatusErrorResponse(Throwable error, BlobIds calendarEventBlobIds, MailboxSession mailboxSession) {
        return Flux.fromIterable(CollectionConverters.SeqHasAsJava(calendarEventBlobIds.value()).asJava())
            .map(blobId -> BlobId.of(blobId.value()).get())
            .map(blobId -> CalendarEventReplyResults$.MODULE$.notDone(blobId, error, mailboxSession))
            .reduce(CalendarEventReplyResults$.MODULE$.empty(), CalendarEventReplyResults$.MODULE$::merge);
    }

    private Mono<CalendarEventReplyResults> updateAttendanceStatusForEventBlob(BlobId blobId, AttendanceStatus attendanceStatus, DavUser davUser, MailboxSession mailboxSession) {
        // PartStat partStat = PartStat.DECLINED;
        PartStat partStat = attendanceStatus.toPartStat().orElseThrow();
        return retrieveEventUid(String.valueOf(blobId.value()), mailboxSession)
            .flatMap(eventUid -> davClient.getCalendarObjectContainingVEvent(davUser, eventUid))
            .flatMap(calendarObjectContainingVEvent ->
                davClient.updateCalendarObject(davUser, calendarObjectContainingVEvent.uri(),
                (calendarObject -> doUpdateAttendanceStatusInCalendarObject(calendarObject, davUser, partStat))))
            .thenReturn(CalendarEventReplyResults$.MODULE$.done(blobId))
            .onErrorResume(e -> Mono.just(CalendarEventReplyResults$.MODULE$.notDone(blobId, e, mailboxSession)));
    }

    private DavCalendarObject doUpdateAttendanceStatusInCalendarObject(DavCalendarObject calendarObject, DavUser davUser, PartStat partStat) {
        Calendar updatedCalendarData = calendarObject.calendarData().copy();
        LOGGER.trace("Calendar to update: {}", calendarObject.calendarData());

        List<VEvent> updatedVevents = updatedCalendarData.<VEvent>getComponents(Component.VEVENT)
            .stream().map(vEvent -> {
                    Optional<Attendee> maybeUserAttendee = vEvent.getAttendees()
                        .stream()
                        .filter(attendee -> davUser.username().equals(attendee.getCalAddress().toASCIIString().substring("MAILTO:".length())))
                        .findAny();

                    return maybeUserAttendee.map(attendee ->
                            vEvent.with(RelationshipPropertyModifiers.ATTENDEE,
                                attendee.replace(partStat)))
                        .map(VEvent.class::cast)
                        .orElse(vEvent);
                }
            ).toList();

        updatedCalendarData.setComponentList(new ComponentList<>(updatedVevents));

        LOGGER.trace("Calendar updated: {}", updatedCalendarData);

        return new DavCalendarObject(calendarObject.uri(), updatedCalendarData, calendarObject.eTag());
    }

    private Mono<String> retrieveEventUid(String blobId, MailboxSession mailboxSession) {
        return extractMessageId(blobId)
            .flatMap(messageId ->
                Mono.fromDirect(
                    messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.HEADERS, mailboxSession)))
            .flatMap(messageResult ->
                Mono.justOrEmpty(findHeaderByName(messageResult, X_MEETING_UID_HEADER)))
            .map(Header::getValue);
    }

    private Optional<Header> findHeaderByName(MessageResult messageResult, String targetHeaderName) {
        try {
            return Iterators
                .tryFind(messageResult.getHeaders().headers(), header -> header.getName().equals(targetHeaderName))
                .toJavaUtil();
        } catch (Exception e) {
            LOGGER.debug("Failed to find header '{}' in message '{}'", targetHeaderName, messageResult.getMessageId().serialize(), e);
            return Optional.empty();
        }
    }

    private Mono<MessageId> extractMessageId(String blobId) {
        return Mono.fromCallable(() ->
            MessagePartBlobId.tryParse(blobId)
                .map(MessagePartBlobId::getMessageId)
                .map(messageIdFactory::fromString)
                .get());
    }
}
