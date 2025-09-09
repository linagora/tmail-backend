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

import static com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.MAILS_RECEIVED_PER_DAYS_UNLIMITED;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.rate.limiter.api.memory.MemoryRateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

import reactor.core.publisher.Mono;

public class RateLimitingUsernameChangeTaskStepTest {
    private static final Username OLD = Username.of("alice");
    private static final Username NEW = Username.of("bob");
    private static final RateLimitingDefinition RATE_LIMITING_1 = RateLimitingDefinition.builder()
        .mailsSentPerMinute(10L)
        .mailsSentPerHours(100L)
        .mailsSentPerDays(1000L)
        .mailsReceivedPerMinute(20L)
        .mailsReceivedPerHours(200L)
        .mailsReceivedPerDays(2000L)
        .build();
    private static final RateLimitingDefinition RATE_LIMITING_2 = RateLimitingDefinition.builder()
        .mailsSentPerMinute(10L)
        .mailsReceivedPerDays(MAILS_RECEIVED_PER_DAYS_UNLIMITED)
        .build();

    private RateLimitingRepository rateLimitingRepository;
    private RateLimitingUsernameChangeTaskStep testee;

    @BeforeEach
    void setUp() {
        rateLimitingRepository = new MemoryRateLimitingRepository();
        testee = new RateLimitingUsernameChangeTaskStep(rateLimitingRepository);
    }

    @Test
    void shouldMigrateRateLimitingWhenNewUserHasNoLimits() {
        Mono.from(rateLimitingRepository.setRateLimiting(OLD, RATE_LIMITING_1)).block();
        Mono.from(testee.changeUsername(OLD, NEW)).block();

        assertThat(Mono.from(rateLimitingRepository.getRateLimiting(NEW)).block())
            .isEqualTo(RATE_LIMITING_1);
    }

    @Test
    void shouldRevokeOldUserRateLimiting() {
        Mono.from(rateLimitingRepository.setRateLimiting(OLD, RATE_LIMITING_1)).block();
        Mono.from(testee.changeUsername(OLD, NEW)).block();

        assertThat(Mono.from(rateLimitingRepository.getRateLimiting(OLD)).block())
            .isEqualTo(RateLimitingDefinition.EMPTY_RATE_LIMIT);
    }

    @Test
    void shouldNotOverrideNewUserExistingRateLimiting() {
        Mono.from(rateLimitingRepository.setRateLimiting(OLD, RATE_LIMITING_1)).block();
        Mono.from(rateLimitingRepository.setRateLimiting(NEW, RATE_LIMITING_2)).block();

        Mono.from(testee.changeUsername(OLD, NEW)).block();

        assertThat(Mono.from(rateLimitingRepository.getRateLimiting(NEW)).block())
            .isEqualTo(RATE_LIMITING_2);
    }

    @Test
    void shouldSucceedWhenOldUserHasNoPlan() {
        Mono.from(testee.changeUsername(OLD, NEW)).block();

        assertThat(Mono.from(rateLimitingRepository.getRateLimiting(NEW)).block())
            .isEqualTo(RateLimitingDefinition.EMPTY_RATE_LIMIT);
    }
}
