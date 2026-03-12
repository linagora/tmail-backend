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
import org.apache.james.core.Username;
import org.reactivestreams.Publisher;

import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

public class MemorySaaSAccountRepository implements SaaSAccountRepository {
    private final ConcurrentMap<Username, SaaSAccount> userTable = new ConcurrentHashMap<>();
    private final ConcurrentMap<Domain, SaaSAccount> domainTable = new ConcurrentHashMap<>();

    @Override
    public Publisher<SaaSAccount> getSaaSAccount(Username username) {
        return Mono.justOrEmpty(userTable.get(username))
            .switchIfEmpty(Mono.defer(() -> username.getDomainPart()
                .map(domain -> Mono.from(getSaaSAccount(domain)))
                .orElse(Mono.empty())))
            .switchIfEmpty(Mono.just(SaaSAccount.DEFAULT));
    }

    @Override
    public Publisher<Void> upsertSaasAccount(Username username, SaaSAccount saaSAccount) {
        return Mono.fromRunnable(() -> userTable.put(username, saaSAccount));
    }

    @Override
    public Publisher<Void> deleteSaaSAccount(Username username) {
        return Mono.fromRunnable(() -> userTable.remove(username));
    }

    @Override
    public Publisher<SaaSAccount> getSaaSAccount(Domain domain) {
        return Mono.justOrEmpty(domainTable.get(domain));
    }

    @Override
    public Publisher<Void> upsertSaasAccount(Domain domain, SaaSAccount saaSAccount) {
        return Mono.fromRunnable(() -> domainTable.put(domain, saaSAccount));
    }

    @Override
    public Publisher<Void> deleteSaaSAccount(Domain domain) {
        return Mono.fromRunnable(() -> domainTable.remove(domain));
    }
}
