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

package com.linagora.tmail.james.common.probe;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.utils.GuiceProbe;

import com.google.inject.Inject;
import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

import reactor.core.publisher.Mono;

public class RateLimitingProbe implements GuiceProbe {
    private final RateLimitingRepository rateLimitingRepository;

    @Inject
    public RateLimitingProbe(RateLimitingRepository rateLimitingRepository) {
        this.rateLimitingRepository = rateLimitingRepository;
    }

    public RateLimitingDefinition getRateLimiting(Username username) {
        return Mono.from(rateLimitingRepository.getRateLimiting(username))
            .block();
    }

    public RateLimitingDefinition getRateLimiting(Domain domain) {
        return Mono.from(rateLimitingRepository.getRateLimiting(domain))
            .block();
    }
}
