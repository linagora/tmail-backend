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
        MailboxSession systemMailboxSession = sessionProvider.createSystemSession(username);

        return getFlags(messageId, systemMailboxSession)
            .flatMap(userFlags -> Mono.justOrEmpty(AttendanceStatus.fromMessageFlags(userFlags)))
            .switchIfEmpty(handleMissingEventAttendanceFlag(messageId, systemMailboxSession));
    }

    private Mono<AttendanceStatus> handleMissingEventAttendanceFlag(MessageId messageId, MailboxSession systemMailboxSession) {
        return Mono.from(getFlags(messageId, systemMailboxSession)).map(flags -> {
            LOGGER.warn("No event attendance flag found for message {}", messageId);
            LOGGER.warn("Flags for message {}: {}", messageId, flags);
            LOGGER.warn("Defaulting to NeedsAction for message {}", messageId);
            return AttendanceStatus.NeedsAction;
        });
    }

    @Override
    public Publisher<Void> setAttendanceStatus(Username username, MessageId messageId,
                                               AttendanceStatus attendanceStatus) {
        MailboxSession systemMailboxSession = sessionProvider.createSystemSession(username);

        return getFlags(messageId, systemMailboxSession)
            .flatMap(flags -> {
                AttendanceStatus.getEventAttendanceFlags().forEach(flags::remove);
                flags.add(attendanceStatus.getUserFlag());
                return doSetFlags(messageId, flags, systemMailboxSession);
            }).then();
    }

    private Mono<Void> doSetFlags(MessageId messageId, Flags flagsToSet, MailboxSession session) {
        return Mono.from(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.MINIMAL, session))
            .map(MessageResult::getMailboxId)
            .flux()
            .collectList()
            .flatMap(mailboxIds ->
                Mono.from(messageIdManager.setFlagsReactive(
                    flagsToSet,
                    MessageManager.FlagsUpdateMode.REPLACE,
                    messageId,
                    mailboxIds,
                    session
                ))
            );
    }

    private Mono<Flags> getFlags(MessageId messageId, MailboxSession session) {
        return Mono.from(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.MINIMAL, session))
            .map(MessageResult::getFlags);
    }
}
