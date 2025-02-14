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

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.jmap.core.AccountId;
import org.apache.james.jmap.mail.BlobId;
import org.apache.james.jmap.mail.BlobIds;
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

import com.linagora.tmail.james.jmap.method.CalendarEventReplyPerformer;
import com.linagora.tmail.james.jmap.model.CalendarEventAttendanceResults;
import com.linagora.tmail.james.jmap.model.CalendarEventAttendanceResults$;
import com.linagora.tmail.james.jmap.model.CalendarEventReplyRequest;
import com.linagora.tmail.james.jmap.model.CalendarEventReplyResults;
import com.linagora.tmail.james.jmap.model.EventAttendanceStatusEntry;
import com.linagora.tmail.james.jmap.model.LanguageLocation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.collection.JavaConverters;
import scala.compat.java8.OptionConverters;

public class StandaloneEventAttendanceRepository implements EventAttendanceRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(StandaloneEventAttendanceRepository.class);

    private final MessageIdManager messageIdManager;
    private final SessionProvider sessionProvider;
    private final CalendarEventReplyPerformer calendarEventReplyPerformer;
    private final MessageId.Factory messageIdFactory;

    @Inject
    public StandaloneEventAttendanceRepository(MessageIdManager messageIdManager, SessionProvider sessionProvider,
                                               CalendarEventReplyPerformer calendarEventReplyPerformer, MessageId.Factory messageIdFactory) {
        this.messageIdManager = messageIdManager;
        this.sessionProvider = sessionProvider;
        this.calendarEventReplyPerformer = calendarEventReplyPerformer;
        this.messageIdFactory = messageIdFactory;
    }

    @Override
    public Publisher<CalendarEventAttendanceResults> getAttendanceStatus(Username username, BlobIds calendarEventBlobIds) {
        LOGGER.trace("Getting attendance status for user '{}' and message '{}'", username,
            calendarEventBlobIds);
        MailboxSession systemMailboxSession = sessionProvider.createSystemSession(username);

        return Flux.fromIterable(JavaConverters.seqAsJavaList(calendarEventBlobIds.value()))
            .flatMap(blobId -> getAttendanceStatusFromEventBlob(blobId.value(), systemMailboxSession))
            .reduce(CalendarEventAttendanceResults$.MODULE$.empty(), CalendarEventAttendanceResults$.MODULE$::merge);
    }

    private Mono<CalendarEventAttendanceResults> getAttendanceStatusFromEventBlob(String blobId, MailboxSession systemMailboxSession) {
        return extractMessageId(blobId)
            .flatMap(messageId ->
                Mono.fromDirect(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.MINIMAL, systemMailboxSession))
                    .flatMap(messageResult -> getAttendanceStatusFromMessage(messageResult, blobId))
                    .defaultIfEmpty(CalendarEventAttendanceResults$.MODULE$.notFound(BlobId.of(blobId).get())))
            .onErrorResume(Exception.class, (error) ->
                Mono.just(CalendarEventAttendanceResults$.MODULE$.notDone(
                    BlobId.of(blobId).get(), error, systemMailboxSession)));
    }

    private Mono<CalendarEventAttendanceResults> getAttendanceStatusFromMessage(MessageResult messageResult, String blobId) {
        return Mono.just(messageResult.getFlags())
            .flatMap(userFlags ->
                Mono.justOrEmpty(AttendanceStatus.fromMessageFlags(userFlags))
                    .map(attendanceStatus ->
                        CalendarEventAttendanceResults$.MODULE$.done(
                            new EventAttendanceStatusEntry(blobId, attendanceStatus))))
            .defaultIfEmpty(handleMissingEventAttendanceFlag(blobId));
    }

    private CalendarEventAttendanceResults handleMissingEventAttendanceFlag(String blobId) {
        LOGGER.debug("""
                No event attendance flag found for blob: {}.
                Defaulting to NeedsAction
                """, blobId);
        return CalendarEventAttendanceResults$.MODULE$.done(
            new EventAttendanceStatusEntry(blobId, AttendanceStatus.NeedsAction));
    }

    @Override
    public Publisher<CalendarEventReplyResults> setAttendanceStatus(Username username, AttendanceStatus attendanceStatus,
                                                                    BlobIds calendarEventBlobIds,
                                                                    Optional<LanguageLocation> maybePreferredLanguage) {
        MailboxSession systemMailboxSession = sessionProvider.createSystemSession(username);

        return Flux.fromIterable(JavaConverters.seqAsJavaList(calendarEventBlobIds.value()))
            .flatMap(blobId -> extractMessageId(blobId.value()))
            .onErrorContinue((throwable, o) -> LOGGER.debug("Failed to extract message id from blob id: {}", o, throwable))
            .collectList()
            .flatMap(messageIds ->
                Flux.from(messageIdManager.getMessagesReactive(messageIds, FetchGroup.MINIMAL, systemMailboxSession))
                    .flatMap(messageResult ->
                        updateEventAttendanceFlags(
                            messageResult,
                            attendanceStatus,
                            systemMailboxSession)).then())
            .then(tryToSendReplyEmail(username, calendarEventBlobIds, maybePreferredLanguage, systemMailboxSession, attendanceStatus));
    }

    private Mono<MessageId> extractMessageId(String blobId) {
        return Mono.fromCallable(() ->
            MessagePartBlobId.tryParse(blobId)
                .map(MessagePartBlobId::getMessageId)
                .map(messageIdFactory::fromString)
                .get());
    }

    private Mono<CalendarEventReplyResults> tryToSendReplyEmail(Username username,
                                                                BlobIds eventBlobIds,
                                                                Optional<LanguageLocation> maybePreferredLanguage,
                                                                MailboxSession systemMailboxSession,
                                                                AttendanceStatus attendanceStatus) {
        return Mono.just(AccountId.from(username))
            .flatMap(accountIdEither ->
                accountIdEither.fold(
                    exception ->
                        Mono.error(new IllegalArgumentException("Failed to get account id from username: " + username, exception)),
                    accountId ->
                        doSendReplyEmail(accountId, systemMailboxSession, eventBlobIds, maybePreferredLanguage, attendanceStatus)));
    }

    private Mono<CalendarEventReplyResults> doSendReplyEmail(AccountId accountId,
                                                             MailboxSession session,
                                                             BlobIds eventBlobIds,
                                                             Optional<LanguageLocation> maybePreferredLanguage,
                                                             AttendanceStatus attendanceStatus) {
        return Mono.justOrEmpty(attendanceStatus.toPartStat())
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid attendance status: " + attendanceStatus)))
            .flatMap(partStat -> Mono.from(
                    calendarEventReplyPerformer.process(
                        new CalendarEventReplyRequest(accountId, eventBlobIds,
                        OptionConverters.toScala(maybePreferredLanguage)),
                        session, partStat)));
    }

    private Flux<Void> updateEventAttendanceFlags(MessageResult message, AttendanceStatus attendanceStatus,
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
                session)
        );
    }
}
