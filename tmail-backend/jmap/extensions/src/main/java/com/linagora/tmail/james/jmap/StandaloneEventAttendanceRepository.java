package com.linagora.tmail.james.jmap;

import java.util.Arrays;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class StandaloneEventAttendanceRepository implements EventAttendanceRepository {
    private static Logger LOGGER = LoggerFactory.getLogger(StandaloneEventAttendanceRepository.class);

    private static final List<String> EVENT_ATTENDANCE_FLAGS = List.of("$accepted", "$rejected", "$tentativelyaccepted");

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

        return Mono.from(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.MINIMAL, systemMailboxSession))
            .map(MessageResult::getFlags)
            .map(this::getAttendanceStatusFromFlags);
    }

    private AttendanceStatus getAttendanceStatusFromFlags(Flags flags) {
        long eventAttendanceFlagsCount = Arrays.stream(flags.getUserFlags())
            .filter(EVENT_ATTENDANCE_FLAGS::contains)
            .count();

        if (eventAttendanceFlagsCount > 1) {
            LOGGER.warn("A message should not have more than one event attendance flag");
            return AttendanceStatus.NeedsAction;
        }

        if (flags.contains("$accepted")) {
            return AttendanceStatus.Accepted;
        } else if (flags.contains("$rejected")) {
            return AttendanceStatus.Declined;
        } else if (flags.contains("$tentativelyaccepted")) {
            return AttendanceStatus.Tentative;
        } else {
            return AttendanceStatus.NeedsAction;
        }
    }

    @Override
    public Publisher<Void> setAttendanceStatus(Username username, MessageId messageId,
                                               AttendanceStatus attendanceStatus) {
        return null;
    }
}
