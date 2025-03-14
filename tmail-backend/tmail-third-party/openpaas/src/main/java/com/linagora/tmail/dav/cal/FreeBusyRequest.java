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

package com.linagora.tmail.dav.cal;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.linagora.tmail.james.jmap.model.CalendarEventParsed;

public record FreeBusyRequest(Instant start,
                              Instant end,
                              List<String> users,
                              List<String> uids) {

    public static Optional<FreeBusyRequest.Builder> tryFromCalendarEventParsed(CalendarEventParsed calendarEventParsed) {
        return calendarEventParsed.startAsJava()
            .flatMap(start -> calendarEventParsed.endAsJava()
                .flatMap(end -> calendarEventParsed.uidAsString().map(
                    uid -> FreeBusyRequest.builder().start(start).end(end).uid(uid))));
    }

    public FreeBusyRequest {
        Preconditions.checkNotNull(start, "Start date cannot be null");
        Preconditions.checkNotNull(end, "End date cannot be null");
        Preconditions.checkArgument(start.isBefore(end), "Start date cannot be after end date, start: %s, end: %s", start, end);
        Preconditions.checkArgument(users != null && users.size() == 1, "Only one user is allowed");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Instant start;
        private Instant end;
        private List<String> users;
        private List<String> uids;

        public Builder start(Instant start) {
            this.start = start;
            return this;
        }

        public Builder start(ZonedDateTime start) {
            this.start = start.toInstant();
            return this;
        }

        public Builder end(Instant end) {
            this.end = end;
            return this;
        }

        public Builder end(ZonedDateTime end) {
            this.end = end.toInstant();
            return this;
        }

        public Builder user(String user) {
            this.users = List.of(user);
            return this;
        }

        public Builder uid(String uid) {
            this.uids = List.of(uid);
            return this;
        }

        public FreeBusyRequest build() {
            return new FreeBusyRequest(start, end, users, uids);
        }
    }

    public byte[] serializeAsBytes() {
        return FreeBusySerializer.INSTANCE.serializeAsBytes(this);
    }
}