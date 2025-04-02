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

import org.apache.james.core.Username;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryOpenPaaSUserDAO implements OpenPaaSUserDAO {
    private final ConcurrentHashMap<Username, OpenPaaSUser> hashMap = new ConcurrentHashMap();

    @Override
    public Mono<OpenPaaSUser> retrieve(OpenPaaSId id) {
        return Mono.fromCallable(() -> hashMap.entrySet().stream()
            .map(Map.Entry::getValue)
            .filter(entry -> entry.id().equals(id))
            .findAny())
            .handle(publishIfPresent());
    }

    @Override
    public Mono<OpenPaaSUser> retrieve(Username username) {
        return Mono.fromCallable(() -> Optional.ofNullable(hashMap.get(username)))
            .handle(publishIfPresent());
    }

    @Override
    public Mono<OpenPaaSUser> add(Username username) {
        OpenPaaSId id = new OpenPaaSId(UUID.randomUUID().toString());

        return Mono.fromCallable(() -> hashMap.computeIfAbsent(username, name -> new OpenPaaSUser(name, id)))
            .filter(result -> result.id().equals(id))
            .switchIfEmpty(Mono.error(() -> new IllegalStateException(username.asString() + " already exist")));
    }

    @Override
    public Flux<OpenPaaSUser> list() {
        return Flux.fromIterable(hashMap.values());
    }
}
