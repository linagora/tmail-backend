package com.linagora.tmail.james.jmap;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.core.Username;
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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class StandaloneEventAttendanceRepository implements EventAttendanceRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(StandaloneEventAttendanceRepository.class);

    private final MessageIdManager messageIdManager;
    private final SessionProvider sessionProvider;

    @Inject
    public StandaloneEventAttendanceRepository(MessageIdManager messageIdManager, SessionProvider sessionProvider) {
        this.messageIdManager = messageIdManager;
        this.sessionProvider = sessionProvider;
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
    public Publisher<Void> setAttendanceStatus(Username username, MessageId messageId,
                                               AttendanceStatus attendanceStatus) {
        MailboxSession systemMailboxSession = sessionProvider.createSystemSession(username);

        return updateEventAttendanceFlags(messageId, attendanceStatus, systemMailboxSession);
    }

    private Mono<Void> updateEventAttendanceFlags(MessageId messageId, AttendanceStatus attendanceStatus, MailboxSession session) {
        return Flux.from(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.MINIMAL, session))
            .map(MessageResult::getMailboxId)
            .collectList()
            .flatMap(mailboxIds ->
                Flux.concat(
                    messageIdManager.setFlagsReactive(
                        AttendanceStatus.getEventAttendanceFlags(),
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
                ).then());
    }

    private Flux<Flags> getFlags(MessageId messageId, MailboxSession session) {
        return Flux.from(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.MINIMAL, session))
            .map(MessageResult::getFlags);
    }
}
