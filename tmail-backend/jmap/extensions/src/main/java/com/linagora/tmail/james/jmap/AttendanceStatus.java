package com.linagora.tmail.james.jmap;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.mail.Flags;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public enum AttendanceStatus {
    Accepted("$accepted"),
    Declined("$rejected"),
    Tentative("$tentativelyaccepted"),
    NeedsAction("$needs-action");

    private static final Set<String> EVENT_ATTENDANCE_FLAGS = Arrays.stream(values())
        .map(AttendanceStatus::getUserFlag)
        .collect(Collectors.toSet());

    private static final Logger LOGGER = LoggerFactory.getLogger(AttendanceStatus.class);

    private final String userFlag;

    AttendanceStatus(String userFlag) {
        this.userFlag = userFlag;
    }

    public String getUserFlag() {
        return userFlag;
    }

    public static Optional<AttendanceStatus> fromUseFlag(String userFlag) {
        Preconditions.checkNotNull(userFlag);
        for (AttendanceStatus status : AttendanceStatus.values()) {
            if (status.userFlag.equals(userFlag)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }

    public static Optional<AttendanceStatus> fromMessageFlags(Flags flags) {
        List<AttendanceStatus> eventAttendanceFlags = Arrays.stream(flags.getUserFlags())
            .filter(EVENT_ATTENDANCE_FLAGS::contains)
            .flatMap(flag -> AttendanceStatus.fromUseFlag(flag).stream())
            .toList();

        if (eventAttendanceFlags.size() > 1) {
            LOGGER.info("Unexpected: Multiple event attendance flags found: {}", eventAttendanceFlags);
        }

        return eventAttendanceFlags.stream().findAny();
    }

    public static Set<String> getEventAttendanceFlags() {
        return EVENT_ATTENDANCE_FLAGS;
    }
}
