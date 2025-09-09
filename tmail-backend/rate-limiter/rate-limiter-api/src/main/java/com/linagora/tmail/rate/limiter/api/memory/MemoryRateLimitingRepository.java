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

package com.linagora.tmail.rate.limiter.api.memory;

import static com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.EMPTY_RATE_LIMIT;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.james.core.Username;
import org.reactivestreams.Publisher;

import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

import reactor.core.publisher.Mono;

public class MemoryRateLimitingRepository implements RateLimitingRepository {
    private final ConcurrentMap<Username, RateLimitingDefinition> table = new ConcurrentHashMap<>();

    @Override
    public Publisher<Void> setRateLimiting(Username username, RateLimitingDefinition rateLimiting) {
        return Mono.fromRunnable(() -> table.put(username, rateLimiting));
    }

    @Override
    public Publisher<RateLimitingDefinition> getRateLimiting(Username username) {
        return Mono.justOrEmpty(table.get(username))
            .defaultIfEmpty(EMPTY_RATE_LIMIT);
    }

    @Override
    public Publisher<Void> revokeRateLimiting(Username username) {
        return Mono.fromRunnable(() -> table.remove(username));
    }
}
