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
import com.linagora.tmail.james.jmap.model.CalendarAttendeeParticipationStatus;

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

    public static Optional<AttendanceStatus> fromCalendarAttendeeParticipationStatus(CalendarAttendeeParticipationStatus status) {
        PartStat partstat = new PartStat.Factory().createParameter(status.value());
        return fromPartStat(partstat);
    }

    public PartStat toPartStat() {
        return switch (this) {
            case Accepted -> PartStat.ACCEPTED;
            case Declined -> PartStat.DECLINED;
            case Tentative -> PartStat.TENTATIVE;
            case NeedsAction -> PartStat.NEEDS_ACTION;
        };
    }

    public static Optional<AttendanceStatus> fromPartStat(PartStat partStat) {
        if (partStat.equals(PartStat.ACCEPTED)) {
            return Optional.of(Accepted);
        } else if (partStat.equals(PartStat.DECLINED)) {
            return Optional.of(Declined);
        } else if (partStat.equals(PartStat.NEEDS_ACTION)) {
            return Optional.of(NeedsAction);
        } else if (partStat.equals(PartStat.TENTATIVE)) {
            return Optional.of(Tentative);
        } else {
            LOGGER.trace("Unable to map PartStat '{}' to AttendanceStatus.", partStat);
            return Optional.empty();
        }
    }

    public static Flags getEventAttendanceFlags() {
        return FlagsBuilder.builder()
            .add(EVENT_ATTENDANCE_FLAGS.toArray(new String[0]))
            .build();
    }
}
