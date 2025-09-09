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

package com.linagora.tmail.rate.limiter.api;

import static com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.EMPTY_RATE_LIMIT;
import static com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.MAILS_RECEIVED_PER_DAYS_UNLIMITED;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

import reactor.core.publisher.Mono;

public interface RateLimitingRepositoryContract {
    Username BOB = Username.of("bob@domain.tld");
    Username ALICE = Username.of("alice@domain.tld");
    RateLimitingDefinition RATE_LIMITING_1 = RateLimitingDefinition.builder()
        .mailsSentPerMinute(10L)
        .mailsSentPerHours(100L)
        .mailsSentPerDays(1000L)
        .mailsReceivedPerMinute(20L)
        .mailsReceivedPerHours(200L)
        .mailsReceivedPerDays(2000L)
        .build();
    RateLimitingDefinition RATE_LIMITING_2 = RateLimitingDefinition.builder()
        .mailsSentPerMinute(10L)
        .mailsReceivedPerDays(MAILS_RECEIVED_PER_DAYS_UNLIMITED)
        .build();

    RateLimitingRepository testee();

    @Test
    default void getRateLimitingDefinitionShouldReturnEmptyRateLimitByDefault() {
        assertThat(Mono.from(testee().getRateLimiting(BOB)).block())
            .isEqualTo(EMPTY_RATE_LIMIT);
    }

    @Test
    default void setRateLimitingDefinitionShouldSucceed() {
        Mono.from(testee().setRateLimiting(BOB, RATE_LIMITING_1)).block();

        assertThat(Mono.from(testee().getRateLimiting(BOB)).block())
            .isEqualTo(RATE_LIMITING_1);
    }

    @Test
    default void setRateLimitingDefinitionShouldOverridePreviousRateLimit() {
        Mono.from(testee().setRateLimiting(BOB, RATE_LIMITING_1)).block();
        Mono.from(testee().setRateLimiting(BOB, RATE_LIMITING_2)).block();

        assertThat(Mono.from(testee().getRateLimiting(BOB)).block())
            .isEqualTo(RATE_LIMITING_2);
    }

    @Test
    default void revokeRateLimitingShouldSucceed() {
        Mono.from(testee().setRateLimiting(BOB, RATE_LIMITING_1)).block();
        Mono.from(testee().revokeRateLimiting(BOB)).block();

        assertThat(Mono.from(testee().getRateLimiting(BOB)).block())
            .isEqualTo(EMPTY_RATE_LIMIT);
    }

    @Test
    default void setRateLimitingAfterRevokeRateLimitingShouldSucceed() {
        // This test simulates a scenario where the user record exists but has no rate limiting yet.
        Mono.from(testee().setRateLimiting(BOB, RATE_LIMITING_1)).block();
        Mono.from(testee().revokeRateLimiting(BOB)).block();

        Mono.from(testee().setRateLimiting(BOB, RATE_LIMITING_2)).block();

        assertThat(Mono.from(testee().getRateLimiting(BOB)).block())
            .isEqualTo(RATE_LIMITING_2);
    }

    @Test
    default void revokeRateLimitingShouldBeIdempotent() {
        Mono.from(testee().setRateLimiting(BOB, RATE_LIMITING_1)).block();

        Mono.from(testee().revokeRateLimiting(BOB)).block();
        Mono.from(testee().revokeRateLimiting(BOB)).block();

        assertThat(Mono.from(testee().getRateLimiting(BOB)).block())
            .isEqualTo(EMPTY_RATE_LIMIT);
    }
}
