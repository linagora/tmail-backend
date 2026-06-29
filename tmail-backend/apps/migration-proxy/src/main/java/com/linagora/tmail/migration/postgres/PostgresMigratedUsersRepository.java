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

package com.linagora.tmail.migration.postgres;

import jakarta.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;

import com.linagora.tmail.migration.core.MigratedUsersRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMigratedUsersRepository implements MigratedUsersRepository {
    private final PostgresMigratedUsersDAO dao;

    @Inject
    public PostgresMigratedUsersRepository(PostgresExecutor.Factory executorFactory) {
        this.dao = new PostgresMigratedUsersDAO(executorFactory.create());
    }

    @Override
    public Mono<Void> addMigratedUser(Username username) {
        return dao.insert(username);
    }

    @Override
    public Mono<Void> removeMigratedUser(Username username) {
        return dao.delete(username);
    }

    @Override
    public Mono<Boolean> isMigrated(Username username) {
        return dao.exists(username);
    }

    @Override
    public Flux<Username> listMigratedUsers() {
        return dao.list();
    }
}
