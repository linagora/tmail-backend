package com.linagora.tmail.james.jmap;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
    private static final List<String> EVENT_ATTENDANCE_FLAGS = List.of("$accepted", "$rejected", "$tentativelyaccepted", "$needs-action");

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
            .flatMap(this::getAttendanceStatusFromFlags)
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
                EVENT_ATTENDANCE_FLAGS.forEach(flags::remove);
                String attendanceFlag =
                    getFlagFromAttendanceStatus(attendanceStatus).orElseGet(() -> {
                        LOGGER.warn("No flag found for attendance status {}", attendanceStatus);
                        LOGGER.warn("Falling back to $needs-action");
                        return "$needs-action";
                    });
                flags.add(attendanceFlag);
                return doSetFlags(messageId, flags, systemMailboxSession);
            }).then();
    }

    private Optional<String> getFlagFromAttendanceStatus(AttendanceStatus attendanceStatus) {
        return switch (attendanceStatus) {
            case Accepted -> Optional.of("$accepted");
            case Declined -> Optional.of("$rejected");
            case Tentative -> Optional.of("$tentativelyaccepted");
            case NeedsAction -> Optional.of("$needs-action");
            case Delegated -> Optional.empty();
        };
    }

    private Mono<AttendanceStatus> getAttendanceStatusFromFlags(Flags flags) {
        long eventAttendanceFlagsCount = Arrays.stream(flags.getUserFlags())
            .filter(EVENT_ATTENDANCE_FLAGS::contains)
            .count();

        if (eventAttendanceFlagsCount > 1) {
            LOGGER.warn("A message should not have more than one event attendance flag");
        }

        if (flags.contains("$accepted")) {
            return Mono.just(AttendanceStatus.Accepted);
        } else if (flags.contains("$rejected")) {
            return Mono.just(AttendanceStatus.Declined);
        } else if (flags.contains("$tentativelyaccepted")) {
            return Mono.just(AttendanceStatus.Tentative);
        } else if (flags.contains("$needs-action")) {
            return Mono.just(AttendanceStatus.NeedsAction);
        } else {
            return Mono.empty();
        }
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
            )
            .onErrorContinue((error, object) ->
                LOGGER.error("Error while setting flags for message ID {}", messageId, error)
            );
    }

    private Mono<Flags> getFlags(MessageId messageId, MailboxSession session) {
        return Mono.from(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.MINIMAL, session))
            .map(MessageResult::getFlags);
    }
}
