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
import com.linagora.tmail.james.jmap.model.EventAttendanceStatusEntry;
import com.linagora.tmail.james.jmap.model.LanguageLocation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.collection.JavaConverters;

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
        MailboxSession systemMailboxSession = sessionProvider.createSystemSession(username);

        return Flux.fromIterable(JavaConverters.seqAsJavaList(calendarEventBlobIds.value()))
            .flatMap(blobId -> getEventUid(blobId.value(), systemMailboxSession)
                .flatMap(eventUid ->
                    toDavUser(username)
                        .flatMap(davUser -> davClient.getCalendarObjectContainingVEvent(davUser, eventUid)))
                .map(calendarObject -> getAttendanceStatusFromCalendarObject(
                    BlobId.of(blobId.value()).get(), calendarObject, username, systemMailboxSession)))
            .reduce(CalendarEventAttendanceResults$.MODULE$.empty(), CalendarEventAttendanceResults$.MODULE$::merge);
    }

    private CalendarEventAttendanceResults getAttendanceStatusFromCalendarObject(BlobId blobId,
        DavCalendarObject calendarObject, Username username, MailboxSession mailboxSession) {

        try {
            List<CalendarEventParsed> events = JavaConverters.asJava(CalendarEventParsed.from(calendarObject.calendarData()));

            if (events.size() != 1) {
                if (events.isEmpty()) {
                    LOGGER.debug("""
                No VEvents found in calendar object.
                Returning empty attendance results.
                """);
                    return CalendarEventAttendanceResults.empty();
                } else {
                    LOGGER.debug("""
                Expected exactly one VEvent, but found {} entries. Using the first VEvent.
                This may indicate unhandled recurrent events or a malformed calendar object.
                VEvents: {}
                """, events.size(), events);
                }
            }

            CalendarEventParsed targetVEvent = events.getFirst();

            return targetVEvent.participants()
                .findParticipantByMailTo(username.asMailAddress().toString())
                .flatMap(CalendarAttendeeField::participationStatus)
                .map(p -> CalendarEventAttendanceResults$.MODULE$.done(
                    new EventAttendanceStatusEntry(
                        blobId.value().value(),
                        AttendanceStatus.fromCalendarAttendeeParticipationStatus(p).orElseThrow())
                )).getOrElse(() -> CalendarEventAttendanceResults$.MODULE$.notFound(blobId));

        } catch (Exception e) {
            return CalendarEventAttendanceResults$.MODULE$.notDone(blobId, e, mailboxSession);
        }
    }

    private Mono<DavUser> toDavUser(Username username) {
        try {
            return davUserProvider.provide(username.asMailAddress());
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
        return null;
    }

    private Mono<String> getEventUid(String blobId, MailboxSession mailboxSession) {
        return extractMessageId(blobId)
            .flatMap(messageId ->
                Mono.fromDirect(
                    messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.MINIMAL, mailboxSession)))
            .flatMap(messageResult ->
                Mono.justOrEmpty(extractHeader(messageResult, X_MEETING_UID_HEADER)))
            .map(Header::getValue);
    }

    private Optional<Header> extractHeader(MessageResult messageResult, String targetHeaderName) {
        try {
            return Iterators
                .tryFind(messageResult.getHeaders().headers(), header -> header.getName().equals(targetHeaderName))
                .toJavaUtil();
        } catch (Exception e) {
            LOGGER.debug("Failed to extract header '{}' from message '{}'", targetHeaderName, messageResult.getMessageId().serialize(), e);
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
