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
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

public record RateLimitsDTO(Optional<Long> mailsSentPerMinute,
                            Optional<Long> mailsSentPerHours,
                            Optional<Long> mailsSentPerDays,
                            Optional<Long> mailsReceivedPerMinute,
                            Optional<Long> mailsReceivedPerHours,
                            Optional<Long> mailsReceivedPerDays) {
    @JsonCreator
    public RateLimitsDTO(@JsonProperty("mailsSentPerMinute") Optional<Long> mailsSentPerMinute,
                         @JsonProperty("mailsSentPerHours") Optional<Long> mailsSentPerHours,
                         @JsonProperty("mailsSentPerDays") Optional<Long> mailsSentPerDays,
                         @JsonProperty("mailsReceivedPerMinute") Optional<Long> mailsReceivedPerMinute,
                         @JsonProperty("mailsReceivedPerHours") Optional<Long> mailsReceivedPerHours,
                         @JsonProperty("mailsReceivedPerDays") Optional<Long> mailsReceivedPerDays) {
        this.mailsSentPerMinute = mailsSentPerMinute;
        this.mailsSentPerHours = mailsSentPerHours;
        this.mailsSentPerDays = mailsSentPerDays;
        this.mailsReceivedPerMinute = mailsReceivedPerMinute;
        this.mailsReceivedPerHours = mailsReceivedPerHours;
        this.mailsReceivedPerDays = mailsReceivedPerDays;
    }

    public RateLimitingDefinition toRateLimitingDefinition() {
        return RateLimitingDefinition.builder()
            .mailsSentPerMinute(mailsSentPerMinute.orElse(null))
            .mailsSentPerHours(mailsSentPerHours.orElse(null))
            .mailsSentPerDays(mailsSentPerDays.orElse(null))
            .mailsReceivedPerMinute(mailsReceivedPerMinute.orElse(null))
            .mailsReceivedPerHours(mailsReceivedPerHours.orElse(null))
            .mailsReceivedPerDays(mailsReceivedPerDays.orElse(null))
            .build();
    }
}
