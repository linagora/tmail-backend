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

package com.linagora.tmail.migration.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.core.Username;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryMigratedUsersRepository implements MigratedUsersRepository {
    private final Set<Username> migrated = ConcurrentHashMap.newKeySet();

    @Override
    public Mono<Void> addMigratedUser(Username username) {
        return Mono.fromRunnable(() -> migrated.add(username));
    }

    @Override
    public Mono<Void> removeMigratedUser(Username username) {
        return Mono.fromRunnable(() -> migrated.remove(username));
    }

    @Override
    public Mono<Boolean> isMigrated(Username username) {
        return Mono.fromCallable(() -> migrated.contains(username));
    }

    @Override
    public Flux<Username> listMigratedUsers() {
        return Flux.fromIterable(migrated);
    }
}
