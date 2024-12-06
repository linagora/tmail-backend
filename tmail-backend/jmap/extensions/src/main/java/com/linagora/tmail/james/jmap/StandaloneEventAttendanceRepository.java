package com.linagora.tmail.james.jmap;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
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
            .map(this::getAttendanceStatusFromFlags)
            .flatMap(Mono::justOrEmpty)
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
            .flatMap(this::filterOutEventAttendanceFlags)
            .flatMap(flagsWithoutEventAttendanceFlags -> {
                Flags flagsToSet = new Flags(flagsWithoutEventAttendanceFlags);
                getFlagFromAttendanceStatus(attendanceStatus)
                    .ifPresent(flagsToSet::add);
                return doSetFlags(messageId, flagsToSet, systemMailboxSession);
            })
            .then();
    }

    private Mono<Flags> filterOutEventAttendanceFlags(Flags flags) {
        return Mono.fromSupplier(() -> {
            Flags flagsWithoutEventAttendanceFlags = new Flags();
            Arrays.stream(flags.getSystemFlags()).forEach(flagsWithoutEventAttendanceFlags::add);
            Arrays.stream(flags.getUserFlags())
                .filter(Predicate.not(EVENT_ATTENDANCE_FLAGS::contains))
                .forEach(flagsWithoutEventAttendanceFlags::add);
            return flagsWithoutEventAttendanceFlags;
        });
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

    private Optional<AttendanceStatus> getAttendanceStatusFromFlags(Flags flags) {
        long eventAttendanceFlagsCount = Arrays.stream(flags.getUserFlags())
            .filter(EVENT_ATTENDANCE_FLAGS::contains)
            .count();

        if (eventAttendanceFlagsCount > 1) {
            LOGGER.warn("A message should not have more than one event attendance flag");
        }

        if (flags.contains("$accepted")) {
            return Optional.of(AttendanceStatus.Accepted);
        } else if (flags.contains("$rejected")) {
            return Optional.of(AttendanceStatus.Declined);
        } else if (flags.contains("$tentativelyaccepted")) {
            return Optional.of(AttendanceStatus.Tentative);
        } else if (flags.contains("$needs-action")) {
            return Optional.of(AttendanceStatus.NeedsAction);
        } else {
            return Optional.empty();
        }
    }

    private Mono<Flags> doSetFlags(MessageId messageId, Flags flagsToSet, MailboxSession session) {
        try {
            List<MailboxId> mailboxIds =
                messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, session)
                    .stream().map(MessageResult::getMailboxId).toList();
            messageIdManager.setFlags(flagsToSet, MessageManager.FlagsUpdateMode.REPLACE, messageId, mailboxIds,
                session);

            return Mono.just(flagsToSet);
        } catch (MailboxException e) {
            LOGGER.error("Error while setting flags", e);
            return Mono.empty();
        }
    }

    private Mono<Flags> getFlags(MessageId messageId, MailboxSession session) {
        return Mono.from(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.MINIMAL, session))
            .map(MessageResult::getFlags);
    }
}
