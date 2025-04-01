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

package com.linagora.tmail.james.jmap;

import static com.linagora.tmail.james.jmap.model.CalendarEventAttendanceResults.AttendanceResult;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.jmap.mail.BlobId;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.james.jmap.calendar.CalendarEventModifier;
import com.linagora.tmail.james.jmap.model.CalendarEventAttendanceResults;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class StandaloneEventRepository implements CalendarEventRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(StandaloneEventRepository.class);

    private final MessageIdManager messageIdManager;
    private final SessionProvider sessionProvider;
    private final MessageId.Factory messageIdFactory;

    @Inject
    public StandaloneEventRepository(MessageIdManager messageIdManager, SessionProvider sessionProvider,
                                     MessageId.Factory messageIdFactory) {
        this.messageIdManager = messageIdManager;
        this.sessionProvider = sessionProvider;
        this.messageIdFactory = messageIdFactory;
    }

    @Override
    public Publisher<CalendarEventAttendanceResults> getAttendanceStatus(Username username, List<BlobId> calendarEventBlobIds) {
        LOGGER.trace("Getting attendance status for user '{}' and message '{}'", username, calendarEventBlobIds);
        MailboxSession systemMailboxSession = sessionProvider.createSystemSession(username);

        return Flux.fromIterable(calendarEventBlobIds)
            .flatMap(blobId -> getAttendanceStatusFromEventBlob(blobId, systemMailboxSession))
            .reduce(CalendarEventAttendanceResults::merge);
    }

    private Mono<CalendarEventAttendanceResults> getAttendanceStatusFromEventBlob(BlobId blobId, MailboxSession systemMailboxSession) {
        return extractMessageId(blobId.value().toString())
            .flatMap(messageId ->
                Mono.fromDirect(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.MINIMAL, systemMailboxSession))
                    .flatMap(messageResult -> getAttendanceStatusFromMessage(messageResult, blobId.value().toString()))
                    .defaultIfEmpty(AttendanceResult().notFound(blobId)))
            .onErrorResume(Exception.class, (error) -> Mono.just(AttendanceResult().notDone(blobId, error)));
    }

    private Mono<CalendarEventAttendanceResults> getAttendanceStatusFromMessage(MessageResult messageResult, String blobId) {
        return Mono.just(messageResult.getFlags())
            .flatMap(userFlags ->
                Mono.justOrEmpty(AttendanceStatus.fromMessageFlags(userFlags))
                    .map(attendanceStatus -> AttendanceResult().done(blobId, attendanceStatus)))
            .defaultIfEmpty(handleMissingEventAttendanceFlag(blobId));
    }

    private CalendarEventAttendanceResults handleMissingEventAttendanceFlag(String blobId) {
        LOGGER.debug("""
            No event attendance flag found for blob: {}.
            Defaulting to NeedsAction
            """, blobId);
        return AttendanceResult().done(blobId, AttendanceStatus.NeedsAction);
    }

    @Override
    public Publisher<Void> setAttendanceStatus(Username username,
                                               AttendanceStatus attendanceStatus,
                                               BlobId eventBlobId) {
        MailboxSession systemMailboxSession = sessionProvider.createSystemSession(username);

        return extractMessageId(eventBlobId.value().toString())
            .doOnError(error -> LOGGER.debug("Failed to extract message id from blob id: {}", eventBlobId.value().toString(), error))
            .flatMap(messageId -> Flux.from(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.MINIMAL, systemMailboxSession))
                .next()
                .switchIfEmpty(Mono.defer(() -> Mono.error(new IllegalArgumentException("No messageId: " + messageId.serialize()))))
                .flatMap(messageResult ->
                    updateEventAttendanceFlags(
                        messageResult,
                        attendanceStatus,
                        systemMailboxSession)));
    }

    private Mono<MessageId> extractMessageId(String blobId) {
        return Mono.fromCallable(() ->
            MessagePartBlobId.tryParse(messageIdFactory, blobId)
                .map(MessagePartBlobId::getMessageId)
                .get());
    }

    private Mono<Void> updateEventAttendanceFlags(MessageResult message, AttendanceStatus attendanceStatus,
                                                  MailboxSession session) {
        // By removing the current event attendance flags (without the new flag to set) we ensure
        // commutativity of operations that is it does not matter whether the REMOVE or ADD operation is done first.
        Flags eventAttendanceFlagsToRemove = AttendanceStatus.getEventAttendanceFlags();
        eventAttendanceFlagsToRemove.remove(attendanceStatus.getUserFlag());
        return Flux.concat(
            messageIdManager.setFlagsReactive(
                eventAttendanceFlagsToRemove,
                MessageManager.FlagsUpdateMode.REMOVE,
                message.getMessageId(),
                List.of(message.getMailboxId()),
                session),
            messageIdManager.setFlagsReactive(
                new Flags(attendanceStatus.getUserFlag()),
                MessageManager.FlagsUpdateMode.ADD,
                message.getMessageId(),
                List.of(message.getMailboxId()),
                session)).then();
    }

    @Override
    public Publisher<Void> updateEvent(Username username, String eventUid, CalendarEventModifier patches) {
        return Mono.error(new UnsupportedOperationException("updateEvent is not supported."));
    }
}
