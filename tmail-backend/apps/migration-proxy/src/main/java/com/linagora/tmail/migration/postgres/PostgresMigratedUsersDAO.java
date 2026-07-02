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

import static com.linagora.tmail.migration.postgres.MigratedUsersDataDefinition.MigratedUsersTable.TABLE_NAME;
import static com.linagora.tmail.migration.postgres.MigratedUsersDataDefinition.MigratedUsersTable.USERNAME;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMigratedUsersDAO {
    private final PostgresExecutor postgresExecutor;

    public PostgresMigratedUsersDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<Void> insert(Username username) {
        return postgresExecutor.executeVoid(dsl -> Mono.from(dsl.insertInto(TABLE_NAME)
            .set(USERNAME, username.asString())
            .onConflict(USERNAME)
            .doNothing()));
    }

    public Mono<Void> delete(Username username) {
        return postgresExecutor.executeVoid(dsl -> Mono.from(dsl.deleteFrom(TABLE_NAME)
            .where(USERNAME.eq(username.asString()))));
    }

    public Mono<Boolean> exists(Username username) {
        return postgresExecutor.executeExists(dsl -> dsl.selectOne()
            .from(TABLE_NAME)
            .where(USERNAME.eq(username.asString())));
    }

    public Flux<Username> list() {
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.select(USERNAME)
                .from(TABLE_NAME)))
            .map(record -> Username.of(record.get(USERNAME)));
    }
}
