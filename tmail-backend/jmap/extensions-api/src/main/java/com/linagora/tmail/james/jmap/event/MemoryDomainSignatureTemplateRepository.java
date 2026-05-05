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

package com.linagora.tmail.james.jmap.event;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.core.Domain;

import reactor.core.publisher.Mono;

public class MemoryDomainSignatureTemplateRepository implements DomainSignatureTemplateRepository {

    private final Map<Domain, DomainSignatureTemplate> store = new ConcurrentHashMap<>();

    @Override
    public Mono<Optional<DomainSignatureTemplate>> get(Domain domain) {
        return Mono.just(Optional.ofNullable(store.get(domain)));
    }

    @Override
    public Mono<Void> store(Domain domain, DomainSignatureTemplate template) {
        return Mono.fromRunnable(() -> store.put(domain, template));
    }

    @Override
    public Mono<Void> delete(Domain domain) {
        return Mono.fromRunnable(() -> store.remove(domain));
    }
}
