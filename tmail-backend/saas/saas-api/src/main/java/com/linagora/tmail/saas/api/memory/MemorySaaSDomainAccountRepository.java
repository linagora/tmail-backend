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

import org.apache.james.core.Domain;
import org.reactivestreams.Publisher;

import com.linagora.tmail.saas.api.SaaSDomainAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

public class MemorySaaSDomainAccountRepository implements SaaSDomainAccountRepository {
    private final ConcurrentMap<Domain, SaaSAccount> table = new ConcurrentHashMap<>();

    @Override
    public Publisher<SaaSAccount> getSaaSDomainAccount(Domain domain) {
        return Mono.justOrEmpty(table.get(domain));
    }

    @Override
    public Publisher<Void> upsertSaasDomainAccount(Domain domain, SaaSAccount saaSAccount) {
        return Mono.fromRunnable(() -> table.put(domain, saaSAccount));
    }

    @Override
    public Publisher<Void> deleteSaaSDomainAccount(Domain domain) {
        return Mono.fromRunnable(() -> table.remove(domain));
    }
}
