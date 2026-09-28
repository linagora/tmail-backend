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

import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

public record RateLimitsDTO(Optional<Long> mailsSentPerMinute,
                            Optional<Long> mailsSentPerHours,
                            Optional<Long> mailsSentPerDays,
                            Optional<Long> mailsReceivedPerMinute,
                            Optional<Long> mailsReceivedPerHours,
                            Optional<Long> mailsReceivedPerDays,
                            Optional<Long> recipientsSentPerMinute,
                            Optional<Long> recipientsSentPerHours,
                            Optional<Long> recipientsSentPerDays) {
    public static RateLimitsDTO from(RateLimitingDefinition rateLimitingDefinition) {
        return new RateLimitsDTO(rateLimitingDefinition.mailsSentPerMinute(),
            rateLimitingDefinition.mailsSentPerHours(),
            rateLimitingDefinition.mailsSentPerDays(),
            rateLimitingDefinition.mailsReceivedPerMinute(),
            rateLimitingDefinition.mailsReceivedPerHours(),
            rateLimitingDefinition.mailsReceivedPerDays(),
            rateLimitingDefinition.recipientsSentPerMinute(),
            rateLimitingDefinition.recipientsSentPerHours(),
            rateLimitingDefinition.recipientsSentPerDays());
    }
}
