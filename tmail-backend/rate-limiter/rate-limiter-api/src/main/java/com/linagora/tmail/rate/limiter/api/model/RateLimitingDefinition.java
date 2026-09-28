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
 *******************************************************************/

package com.linagora.tmail.rate.limiter.api.model;

import java.util.Optional;

public record RateLimitingDefinition(Optional<Long> mailsSentPerMinute,
                                     Optional<Long> mailsSentPerHours,
                                     Optional<Long> mailsSentPerDays,
                                     Optional<Long> mailsReceivedPerMinute,
                                     Optional<Long> mailsReceivedPerHours,
                                     Optional<Long> mailsReceivedPerDays,
                                     Optional<Long> recipientsSentPerMinute,
                                     Optional<Long> recipientsSentPerHours,
                                     Optional<Long> recipientsSentPerDays) {
    public static Long MAILS_SENT_PER_MINUTE_UNLIMITED = -1L;
    public static Long MAILS_SENT_PER_HOURS_UNLIMITED = -1L;
    public static Long MAILS_SENT_PER_DAYS_UNLIMITED = -1L;
    public static Long MAILS_RECEIVED_PER_MINUTE_UNLIMITED = -1L;
    public static Long MAILS_RECEIVED_PER_HOURS_UNLIMITED = -1L;
    public static Long MAILS_RECEIVED_PER_DAYS_UNLIMITED = -1L;
    public static Long RECIPIENTS_SENT_PER_MINUTE_UNLIMITED = -1L;
    public static Long RECIPIENTS_SENT_PER_HOURS_UNLIMITED = -1L;
    public static Long RECIPIENTS_SENT_PER_DAYS_UNLIMITED = -1L;
    public static RateLimitingDefinition EMPTY_RATE_LIMIT = new RateLimitingDefinition(
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
        Optional.empty(), Optional.empty(), Optional.empty());

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<Long> mailsSentPerMinute = Optional.empty();
        private Optional<Long> mailsSentPerHours = Optional.empty();
        private Optional<Long> mailsSentPerDays = Optional.empty();
        private Optional<Long> mailsReceivedPerMinute = Optional.empty();
        private Optional<Long> mailsReceivedPerHours = Optional.empty();
        private Optional<Long> mailsReceivedPerDays = Optional.empty();
        private Optional<Long> recipientsSentPerMinute = Optional.empty();
        private Optional<Long> recipientsSentPerHours = Optional.empty();
        private Optional<Long> recipientsSentPerDays = Optional.empty();

        public Builder mailsSentPerMinute(Long value) {
            this.mailsSentPerMinute = Optional.ofNullable(value);
            return this;
        }

        public Builder mailsSentPerHours(Long value) {
            this.mailsSentPerHours = Optional.ofNullable(value);
            return this;
        }

        public Builder mailsSentPerDays(Long value) {
            this.mailsSentPerDays = Optional.ofNullable(value);
            return this;
        }

        public Builder mailsReceivedPerMinute(Long value) {
            this.mailsReceivedPerMinute = Optional.ofNullable(value);
            return this;
        }

        public Builder mailsReceivedPerHours(Long value) {
            this.mailsReceivedPerHours = Optional.ofNullable(value);
            return this;
        }

        public Builder mailsReceivedPerDays(Long value) {
            this.mailsReceivedPerDays = Optional.ofNullable(value);
            return this;
        }

        public Builder recipientsSentPerMinute(Long value) {
            this.recipientsSentPerMinute = Optional.ofNullable(value);
            return this;
        }

        public Builder recipientsSentPerHours(Long value) {
            this.recipientsSentPerHours = Optional.ofNullable(value);
            return this;
        }

        public Builder recipientsSentPerDays(Long value) {
            this.recipientsSentPerDays = Optional.ofNullable(value);
            return this;
        }

        public RateLimitingDefinition build() {
            return new RateLimitingDefinition(
                mailsSentPerMinute,
                mailsSentPerHours,
                mailsSentPerDays,
                mailsReceivedPerMinute,
                mailsReceivedPerHours,
                mailsReceivedPerDays,
                recipientsSentPerMinute,
                recipientsSentPerHours,
                recipientsSentPerDays);
        }
    }
}
