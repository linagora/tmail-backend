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


package com.linagora.tmail.webadmin.model;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import com.linagora.tmail.rate.limiter.api.model.LimitUpdate;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

/**
 * Omitted mail limits are unset. Recipient limits were introduced later: omitting them leaves the stored
 * value untouched so that clients unaware of them do not erase them. An explicit null unsets them.
 */
public record RateLimitsUpdateDTO(Optional<Long> mailsSentPerMinute,
                                  Optional<Long> mailsSentPerHours,
                                  Optional<Long> mailsSentPerDays,
                                  Optional<Long> mailsReceivedPerMinute,
                                  Optional<Long> mailsReceivedPerHours,
                                  Optional<Long> mailsReceivedPerDays,
                                  LimitUpdate recipientsSentPerMinute,
                                  LimitUpdate recipientsSentPerHours,
                                  LimitUpdate recipientsSentPerDays) {
    @JsonCreator
    public static RateLimitsUpdateDTO fromJson(@JsonProperty("mailsSentPerMinute") Optional<Long> mailsSentPerMinute,
                                               @JsonProperty("mailsSentPerHours") Optional<Long> mailsSentPerHours,
                                               @JsonProperty("mailsSentPerDays") Optional<Long> mailsSentPerDays,
                                               @JsonProperty("mailsReceivedPerMinute") Optional<Long> mailsReceivedPerMinute,
                                               @JsonProperty("mailsReceivedPerHours") Optional<Long> mailsReceivedPerHours,
                                               @JsonProperty("mailsReceivedPerDays") Optional<Long> mailsReceivedPerDays,
                                               @JsonProperty("recipientsSentPerMinute") JsonNode recipientsSentPerMinute,
                                               @JsonProperty("recipientsSentPerHours") JsonNode recipientsSentPerHours,
                                               @JsonProperty("recipientsSentPerDays") JsonNode recipientsSentPerDays) {
        return new RateLimitsUpdateDTO(mailsSentPerMinute, mailsSentPerHours, mailsSentPerDays,
            mailsReceivedPerMinute, mailsReceivedPerHours, mailsReceivedPerDays,
            asLimitUpdate("recipientsSentPerMinute", recipientsSentPerMinute),
            asLimitUpdate("recipientsSentPerHours", recipientsSentPerHours),
            asLimitUpdate("recipientsSentPerDays", recipientsSentPerDays));
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

    public RateLimitingDefinition toRateLimitingDefinition(RateLimitingDefinition current) {
        return RateLimitingDefinition.builder()
            .mailsSentPerMinute(mailsSentPerMinute.orElse(null))
            .mailsSentPerHours(mailsSentPerHours.orElse(null))
            .mailsSentPerDays(mailsSentPerDays.orElse(null))
            .mailsReceivedPerMinute(mailsReceivedPerMinute.orElse(null))
            .mailsReceivedPerHours(mailsReceivedPerHours.orElse(null))
            .mailsReceivedPerDays(mailsReceivedPerDays.orElse(null))
            .recipientsSentPerMinute(recipientsSentPerMinute.applyTo(current.recipientsSentPerMinute()).orElse(null))
            .recipientsSentPerHours(recipientsSentPerHours.applyTo(current.recipientsSentPerHours()).orElse(null))
            .recipientsSentPerDays(recipientsSentPerDays.applyTo(current.recipientsSentPerDays()).orElse(null))
            .build();
    }
}
