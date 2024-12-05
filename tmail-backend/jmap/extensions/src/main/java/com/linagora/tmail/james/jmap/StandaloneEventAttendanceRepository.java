package com.linagora.tmail.james.jmap;

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

import reactor.core.publisher.Mono;

public class StandaloneEventAttendanceRepository implements EventAttendanceRepository {
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
