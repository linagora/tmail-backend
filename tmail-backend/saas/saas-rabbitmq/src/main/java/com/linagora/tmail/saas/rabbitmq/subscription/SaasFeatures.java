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
import com.google.common.base.Preconditions;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

public record SaasFeatures(@JsonProperty("mail") Optional<MailLimitation> mail) {
    public record MailLimitation(
        @JsonProperty("storageQuota") Long storageQuota,
        @JsonProperty("mailsSentPerMinute") Long mailsSentPerMinute,
        @JsonProperty("mailsSentPerHour") Long mailsSentPerHour,
        @JsonProperty("mailsSentPerDay") Long mailsSentPerDay,
        @JsonProperty("mailsReceivedPerMinute") Long mailsReceivedPerMinute,
        @JsonProperty("mailsReceivedPerHour") Long mailsReceivedPerHour,
        @JsonProperty("mailsReceivedPerDay") Long mailsReceivedPerDay) {

        @JsonCreator
        public MailLimitation {
            Preconditions.checkNotNull(storageQuota, "storageQuota cannot be null");
            Preconditions.checkNotNull(mailsSentPerMinute, "mailsSentPerMinute cannot be null");
            Preconditions.checkNotNull(mailsSentPerHour, "mailsSentPerHour cannot be null");
            Preconditions.checkNotNull(mailsSentPerDay, "mailsSentPerDay cannot be null");
            Preconditions.checkNotNull(mailsReceivedPerMinute, "mailsReceivedPerMinute cannot be null");
            Preconditions.checkNotNull(mailsReceivedPerHour, "mailsReceivedPerHour cannot be null");
            Preconditions.checkNotNull(mailsReceivedPerDay, "mailsReceivedPerDay cannot be null");
        }

        public RateLimitingDefinition rateLimitingDefinition() {
            return RateLimitingDefinition.builder()
                .mailsSentPerMinute(mailsSentPerMinute)
                .mailsSentPerHours(mailsSentPerHour)
                .mailsSentPerDays(mailsSentPerDay)
                .mailsReceivedPerMinute(mailsReceivedPerMinute)
                .mailsReceivedPerHours(mailsReceivedPerHour)
                .mailsReceivedPerDays(mailsReceivedPerDay)
                .build();
        }
    }

    @JsonCreator
    public SaasFeatures {

    }
}
