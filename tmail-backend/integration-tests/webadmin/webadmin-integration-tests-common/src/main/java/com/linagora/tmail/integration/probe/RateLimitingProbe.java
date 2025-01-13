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

package com.linagora.tmail.integration.probe;

import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.utils.GuiceProbe;

import com.linagora.tmail.rate.limiter.api.RateLimitingPlan;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanCreateRequest;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanId;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanUserRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RateLimitingProbe implements GuiceProbe {
    private final RateLimitingPlanRepository planRepository;
    private final RateLimitingPlanUserRepository planUserRepository;

    @Inject
    public RateLimitingProbe(RateLimitingPlanRepository planRepository, RateLimitingPlanUserRepository planUserRepository) {
        this.planRepository = planRepository;
        this.planUserRepository = planUserRepository;
    }

    public RateLimitingPlan createPlan(RateLimitingPlanCreateRequest request) {
        return Mono.from(planRepository.create(request)).block();
    }

    public void applyPlan(Username username, RateLimitingPlanId planId) {
        Mono.from(planUserRepository.applyPlan(username, planId)).block();
    }

    public List<Username> listUsersOfAPlan(RateLimitingPlanId planId) {
        return Flux.from(planUserRepository.listUsers(planId))
            .collectList()
            .block();
    }
}
