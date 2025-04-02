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

package com.linagora.calendar.storage;

import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.core.Domain;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryOpenPaaSDomainDAO implements OpenPaaSDomainDAO {
    private final ConcurrentHashMap<Domain, OpenPaaSDomain> hashMap = new ConcurrentHashMap();

    @Override
    public Mono<OpenPaaSDomain> retrieve(OpenPaaSId id) {
        return Mono.fromCallable(() -> hashMap.entrySet().stream()
            .map(Map.Entry::getValue)
            .filter(entry -> entry.id().equals(id))
            .findAny())
            .handle(publishIfPresent());
    }

    @Override
    public Mono<OpenPaaSDomain> retrieve(Domain domain) {
        return Mono.fromCallable(() -> Optional.ofNullable(hashMap.get(domain)))
            .handle(publishIfPresent());
    }

    @Override
    public Mono<OpenPaaSDomain> add(Domain domain) {
        OpenPaaSId id = new OpenPaaSId(UUID.randomUUID().toString());

        return Mono.fromCallable(() -> hashMap.computeIfAbsent(domain, name -> new OpenPaaSDomain(name, id)))
            .filter(result -> result.id().equals(id))
            .switchIfEmpty(Mono.error(() -> new IllegalStateException(domain.asString() + " already exist")));
    }

    @Override
    public Flux<OpenPaaSDomain> list() {
        return Flux.fromIterable(hashMap.values());
    }
}
