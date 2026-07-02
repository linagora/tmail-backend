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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgresMigratedUsersDAOTest {
    private static final Username BOB = Username.of("bob@domain.tld");
    private static final Username ALICE = Username.of("alice@domain.tld");

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(MigratedUsersDataDefinition.MODULE);

    private PostgresMigratedUsersDAO dao;

    @BeforeEach
    void setUp() {
        dao = new PostgresMigratedUsersDAO(postgresExtension.getDefaultPostgresExecutor());
    }

    @Test
    void existsShouldReturnFalseByDefault() {
        assertThat(dao.exists(BOB).block()).isFalse();
    }

    @Test
    void existsShouldReturnTrueAfterInsert() {
        dao.insert(BOB).block();

        assertThat(dao.exists(BOB).block()).isTrue();
    }

    @Test
    void insertShouldBeIdempotent() {
        dao.insert(BOB).block();
        dao.insert(BOB).block();

        assertThat(dao.list().collectList().block()).containsExactly(BOB);
    }

    @Test
    void listShouldReturnInsertedUsers() {
        dao.insert(BOB).block();
        dao.insert(ALICE).block();

        assertThat(dao.list().collectList().block()).containsExactlyInAnyOrder(BOB, ALICE);
    }

    @Test
    void deleteShouldRemoveUser() {
        dao.insert(BOB).block();
        dao.delete(BOB).block();

        assertThat(dao.exists(BOB).block()).isFalse();
    }

    @Test
    void deleteShouldNotThrowWhenAbsent() {
        dao.delete(BOB).block();

        assertThat(dao.exists(BOB).block()).isFalse();
    }
}
