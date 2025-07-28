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

package com.linagora.tmail.saas.api.memory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.james.core.Username;
import org.reactivestreams.Publisher;

import com.linagora.tmail.saas.api.SaaSUserRepository;
import com.linagora.tmail.saas.model.SaaSPlan;

import reactor.core.publisher.Mono;

public class MemorySaaSUserRepository implements SaaSUserRepository {
    private final ConcurrentMap<Username, SaaSPlan> table = new ConcurrentHashMap<>();

    @Override
    public Publisher<SaaSPlan> getPlan(Username username) {
        return Mono.justOrEmpty(table.get(username))
            .switchIfEmpty(Mono.fromCallable(() -> SaaSPlan.FREE));
    }

    @Override
    public Publisher<Void> setPlan(Username username, SaaSPlan saaSPlan) {
        return Mono.fromRunnable(() -> table.put(username, saaSPlan));
    }
}
