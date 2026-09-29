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

package com.linagora.tmail.saas.rabbitmq.subscription;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import com.linagora.tmail.rate.limiter.api.model.LimitUpdate;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

public record SaasFeatures(@JsonProperty("mail") Optional<MailLimitation> mail) {
    public record MailLimitation(
        Long storageQuota,
        Long mailsSentPerMinute,
        Long mailsSentPerHour,
        Long mailsSentPerDay,
        Long mailsReceivedPerMinute,
        Long mailsReceivedPerHour,
        Long mailsReceivedPerDay,
        LimitUpdate recipientsSentPerMinute,
        LimitUpdate recipientsSentPerHour,
        LimitUpdate recipientsSentPerDay) {

        // Recipient limits were introduced after the other limits: an absent field leaves the stored
        // value untouched so that producers unaware of them do not erase limits set by other means.
        @JsonCreator
        public static MailLimitation fromJson(
            @JsonProperty("storageQuota") Long storageQuota,
            @JsonProperty("mailsSentPerMinute") Long mailsSentPerMinute,
            @JsonProperty("mailsSentPerHour") Long mailsSentPerHour,
            @JsonProperty("mailsSentPerDay") Long mailsSentPerDay,
            @JsonProperty("mailsReceivedPerMinute") Long mailsReceivedPerMinute,
            @JsonProperty("mailsReceivedPerHour") Long mailsReceivedPerHour,
            @JsonProperty("mailsReceivedPerDay") Long mailsReceivedPerDay,
            @JsonProperty("recipientsSentPerMinute") JsonNode recipientsSentPerMinute,
            @JsonProperty("recipientsSentPerHour") JsonNode recipientsSentPerHour,
            @JsonProperty("recipientsSentPerDay") JsonNode recipientsSentPerDay) {
            return new MailLimitation(storageQuota,
                mailsSentPerMinute, mailsSentPerHour, mailsSentPerDay,
                mailsReceivedPerMinute, mailsReceivedPerHour, mailsReceivedPerDay,
                asLimitUpdate("recipientsSentPerMinute", recipientsSentPerMinute),
                asLimitUpdate("recipientsSentPerHour", recipientsSentPerHour),
                asLimitUpdate("recipientsSentPerDay", recipientsSentPerDay));
        }

        private static LimitUpdate asLimitUpdate(String field, JsonNode node) {
            if (node == null) {
                return LimitUpdate.KEEP;
            }
            if (node.isNull()) {
                return LimitUpdate.clear();
            }
            Preconditions.checkArgument(node.canConvertToExactIntegral() && node.canConvertToLong(), "%s must be an integer", field);
            return LimitUpdate.replace(node.asLong());
        }

        public MailLimitation(Long storageQuota,
                              Long mailsSentPerMinute,
                              Long mailsSentPerHour,
                              Long mailsSentPerDay,
                              Long mailsReceivedPerMinute,
                              Long mailsReceivedPerHour,
                              Long mailsReceivedPerDay) {
            this(storageQuota, mailsSentPerMinute, mailsSentPerHour, mailsSentPerDay,
                mailsReceivedPerMinute, mailsReceivedPerHour, mailsReceivedPerDay,
                LimitUpdate.KEEP, LimitUpdate.KEEP, LimitUpdate.KEEP);
        }

        public MailLimitation {
            Preconditions.checkNotNull(storageQuota, "storageQuota cannot be null");
            Preconditions.checkNotNull(mailsSentPerMinute, "mailsSentPerMinute cannot be null");
            Preconditions.checkNotNull(mailsSentPerHour, "mailsSentPerHour cannot be null");
            Preconditions.checkNotNull(mailsSentPerDay, "mailsSentPerDay cannot be null");
            Preconditions.checkNotNull(mailsReceivedPerMinute, "mailsReceivedPerMinute cannot be null");
            Preconditions.checkNotNull(mailsReceivedPerHour, "mailsReceivedPerHour cannot be null");
            Preconditions.checkNotNull(mailsReceivedPerDay, "mailsReceivedPerDay cannot be null");
            Preconditions.checkNotNull(recipientsSentPerMinute, "recipientsSentPerMinute cannot be null");
            Preconditions.checkNotNull(recipientsSentPerHour, "recipientsSentPerHour cannot be null");
            Preconditions.checkNotNull(recipientsSentPerDay, "recipientsSentPerDay cannot be null");
        }

        public RateLimitingDefinition rateLimitingDefinition() {
            return rateLimitingDefinition(RateLimitingDefinition.EMPTY_RATE_LIMIT);
        }

        public RateLimitingDefinition rateLimitingDefinition(RateLimitingDefinition current) {
            return RateLimitingDefinition.builder()
                .mailsSentPerMinute(mailsSentPerMinute)
                .mailsSentPerHours(mailsSentPerHour)
                .mailsSentPerDays(mailsSentPerDay)
                .mailsReceivedPerMinute(mailsReceivedPerMinute)
                .mailsReceivedPerHours(mailsReceivedPerHour)
                .mailsReceivedPerDays(mailsReceivedPerDay)
                .recipientsSentPerMinute(recipientsSentPerMinute.applyTo(current.recipientsSentPerMinute()).orElse(null))
                .recipientsSentPerHours(recipientsSentPerHour.applyTo(current.recipientsSentPerHours()).orElse(null))
                .recipientsSentPerDays(recipientsSentPerDay.applyTo(current.recipientsSentPerDays()).orElse(null))
                .build();
        }
    }

    @JsonCreator
    public SaasFeatures {

    }
}
