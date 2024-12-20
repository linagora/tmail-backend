package com.linagora.tmail.james.jmap;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.jmap.core.AccountId;
import org.apache.james.jmap.mail.BlobIds;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.james.jmap.method.CalendarEventReplyPerformer;
import com.linagora.tmail.james.jmap.model.CalendarEventReplyRequest;
import com.linagora.tmail.james.jmap.model.CalendarEventReplyResults;
import com.linagora.tmail.james.jmap.model.LanguageLocation;

import net.fortuna.ical4j.model.parameter.PartStat;
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
    public Publisher<AttendanceStatus> getAttendanceStatus(Username username, MessageId messageId) {
        LOGGER.trace("Getting attendance status for user '{}' and message '{}'", username, messageId);
        MailboxSession systemMailboxSession = sessionProvider.createSystemSession(username);

        return getFlags(messageId, systemMailboxSession)
            .flatMap(userFlags -> Mono.justOrEmpty(AttendanceStatus.fromMessageFlags(userFlags)))
            .switchIfEmpty(handleMissingEventAttendanceFlag(messageId));
    }

    private Mono<AttendanceStatus> handleMissingEventAttendanceFlag(MessageId messageId) {
        LOGGER.debug("""
                No event attendance flag found for message {}.
                Defaulting to NeedsAction
                """, messageId);
        return Mono.just(AttendanceStatus.NeedsAction);
    }

    @Override
    public Publisher<CalendarEventReplyResults> setAttendanceStatus(Username username, AttendanceStatus attendanceStatus,
                                                                    BlobIds eventBlobIds,
                                                                    Optional<LanguageLocation> maybePreferredLanguage) {
        MailboxSession systemMailboxSession = sessionProvider.createSystemSession(username);

        return getEnclosingMessageId(eventBlobIds).flatMap(messageId ->
            Flux.from(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.MINIMAL, systemMailboxSession))
                .map(MessageResult::getMailboxId)
                .collectList()
                .flatMap(mailboxIds ->
                    updateEventAttendanceFlagsInMailboxes(
                        messageId,
                        attendanceStatus,
                        systemMailboxSession,
                        mailboxIds)))
            .then(tryToSendReplyEmail(username, eventBlobIds, maybePreferredLanguage, systemMailboxSession));
    }

    private Mono<CalendarEventReplyResults> tryToSendReplyEmail(Username username, BlobIds eventBlobIds, Optional<LanguageLocation> maybePreferredLanguage, MailboxSession systemMailboxSession) {
        return Mono.just(AccountId.from(username))
            .flatMap(accountIdEither ->
                accountIdEither.fold(
                    exception -> Mono.error(new IllegalArgumentException(
                        "Failed to get account id from username: " + username, exception)),
                    accountId -> doSendReplyEmail(accountId, systemMailboxSession, eventBlobIds,
                        maybePreferredLanguage)));
    }

    private Mono<MessageId> getEnclosingMessageId(BlobIds blobIds) {
        return Flux.fromIterable(JavaConverters.seqAsJavaList(blobIds.value()))
            .flatMap(blobId -> extractMessageId(blobId.value()))
            .distinct()
            .collectList()
            .handle((enclosingMessageIds, sink) -> {
                if (enclosingMessageIds.size() != 1) {
                    sink.error(new IllegalStateException("Expected a single enclosing message for all calendar event blobIds, got: " + enclosingMessageIds));
                } else {
                    sink.next(enclosingMessageIds.getFirst());
                }
            });
    }

    private Mono<MessageId> extractMessageId(String blobId) {
        return Mono.just(blobId)
            .map(blobIdParam -> {
                int underscoreIndex = blobId.indexOf('_');
                String messageId = blobId.substring(0, underscoreIndex);
                return messageIdFactory.fromString(messageId);
            });
    }

    private Mono<CalendarEventReplyResults> doSendReplyEmail(AccountId accountId, MailboxSession session, BlobIds eventBlobIds, Optional<LanguageLocation> maybePreferredLanguage) {
        return Mono.from(calendarEventReplyPerformer.process(
            new CalendarEventReplyRequest(
                accountId,
                eventBlobIds,
                OptionConverters.toScala(maybePreferredLanguage)),
            session,
            PartStat.ACCEPTED));
    }

    private Mono<Void> updateEventAttendanceFlagsInMailboxes(MessageId messageId, AttendanceStatus attendanceStatus,
                                                             MailboxSession session, List<MailboxId> mailboxIds) {
        // By removing the current event attendance flags (without the new flag to set) we ensure
        // commutativity of operations that is it does not matter whether the REMOVE or ADD operation is done first.
        Flags eventAttendanceFlagsToRemove = AttendanceStatus.getEventAttendanceFlags();
        eventAttendanceFlagsToRemove.remove(attendanceStatus.getUserFlag());
        return Flux.concat(
            messageIdManager.setFlagsReactive(
                eventAttendanceFlagsToRemove,
                MessageManager.FlagsUpdateMode.REMOVE,
                messageId,
                mailboxIds,
                session),
            messageIdManager.setFlagsReactive(
                new Flags(attendanceStatus.getUserFlag()),
                MessageManager.FlagsUpdateMode.ADD,
                messageId,
                mailboxIds,
                session)
        ).then();
    }

    private Flux<Flags> getFlags(MessageId messageId, MailboxSession session) {
        return Flux.from(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.MINIMAL, session))
            .map(MessageResult::getFlags);
    }
}
