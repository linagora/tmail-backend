package com.linagora.tmail.james.jmap;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.mail.Flags;

import org.apache.james.mailbox.FlagsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import net.fortuna.ical4j.model.parameter.PartStat;

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

    public static Optional<AttendanceStatus> fromUserFlag(String userFlag) {
        Preconditions.checkNotNull(userFlag);
        return Arrays.stream(AttendanceStatus.values())
            .filter(status -> status.userFlag.equals(userFlag))
            .findFirst();
    }

    public static Optional<AttendanceStatus> fromMessageFlags(Flags flags) {
        List<AttendanceStatus> eventAttendanceFlags = Arrays.stream(flags.getUserFlags())
            .filter(EVENT_ATTENDANCE_FLAGS::contains)
            .flatMap(flag -> AttendanceStatus.fromUserFlag(flag).stream())
            .toList();

        if (eventAttendanceFlags.size() > 1) {
            LOGGER.info("Unexpected: Multiple event attendance flags found: {}", eventAttendanceFlags);
        }

        return eventAttendanceFlags.stream().findAny();
    }

    public Optional<PartStat> toPartStat() {
        return switch (this) {
            case Accepted -> Optional.of(PartStat.ACCEPTED);
            case Declined -> Optional.of(PartStat.DECLINED);
            case Tentative -> Optional.of(PartStat.TENTATIVE);
            case NeedsAction -> Optional.of(PartStat.NEEDS_ACTION);
        };
    }

    public static Flags getEventAttendanceFlags() {
        return FlagsBuilder.builder()
            .add(EVENT_ATTENDANCE_FLAGS.toArray(new String[0]))
            .build();
    }
}
