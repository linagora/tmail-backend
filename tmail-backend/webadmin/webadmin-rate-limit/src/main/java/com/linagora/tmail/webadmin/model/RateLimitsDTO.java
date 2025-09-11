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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

public record RateLimitsDTO(Long mailsSentPerMinute,
                            Long mailsSentPerHours,
                            Long mailsSentPerDays,
                            Long mailsReceivedPerMinute,
                            Long mailsReceivedPerHours,
                            Long mailsReceivedPerDays) {
    @JsonCreator
    public RateLimitsDTO(@JsonProperty("mailsSentPerMinute") Long mailsSentPerMinute,
                         @JsonProperty("mailsSentPerHours") Long mailsSentPerHours,
                         @JsonProperty("mailsSentPerDays") Long mailsSentPerDays,
                         @JsonProperty("mailsReceivedPerMinute") Long mailsReceivedPerMinute,
                         @JsonProperty("mailsReceivedPerHours") Long mailsReceivedPerHours,
                         @JsonProperty("mailsReceivedPerDays") Long mailsReceivedPerDays) {
        this.mailsSentPerMinute = mailsSentPerMinute;
        this.mailsSentPerHours = mailsSentPerHours;
        this.mailsSentPerDays = mailsSentPerDays;
        this.mailsReceivedPerMinute = mailsReceivedPerMinute;
        this.mailsReceivedPerHours = mailsReceivedPerHours;
        this.mailsReceivedPerDays = mailsReceivedPerDays;
    }

    public RateLimitingDefinition toRateLimitingDefinition() {
        return RateLimitingDefinition.builder()
            .mailsSentPerMinute(mailsSentPerMinute)
            .mailsSentPerHours(mailsSentPerHours)
            .mailsSentPerDays(mailsSentPerDays)
            .mailsReceivedPerMinute(mailsReceivedPerMinute)
            .mailsReceivedPerHours(mailsReceivedPerHours)
            .mailsReceivedPerDays(mailsReceivedPerDays)
            .build();
    }
}
