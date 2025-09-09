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

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.reactivestreams.Publisher;

import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

import reactor.core.publisher.Mono;

public class RateLimitingUsernameChangeTaskStep implements UsernameChangeTaskStep {
    private final RateLimitingRepository rateLimitingRepository;

    @Inject
    public RateLimitingUsernameChangeTaskStep(RateLimitingRepository rateLimitingRepository) {
        this.rateLimitingRepository = rateLimitingRepository;
    }

    @Override
    public StepName name() {
        return new StepName("RateLimitingUsernameChangeTaskStep");
    }

    @Override
    public int priority() {
        return 8;
    }

    @Override
    public Publisher<Void> changeUsername(Username oldUsername, Username newUsername) {
        return Mono.from(rateLimitingRepository.getRateLimiting(newUsername))
            .flatMap(newUserLimits -> {
                if (noLimits(newUserLimits)) {
                    return migrateLimits(oldUsername, newUsername);
                }
                return Mono.empty();
            })
            .then(Mono.from(rateLimitingRepository.revokeRateLimiting(oldUsername)));
    }

    private boolean noLimits(RateLimitingDefinition newUserLimits) {
        return newUserLimits.equals(EMPTY_RATE_LIMIT);
    }

    private Mono<Void> migrateLimits(Username oldUsername, Username newUsername) {
        return Mono.from(rateLimitingRepository.getRateLimiting(oldUsername))
            .flatMap(oldUserLimits -> Mono.from(rateLimitingRepository.setRateLimiting(newUsername, oldUserLimits)));
    }
}
