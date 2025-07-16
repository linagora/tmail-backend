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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.user.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Username;
import org.apache.james.user.api.DelegationStore;
import org.apache.james.user.api.DelegationStoreContract;
import org.apache.james.user.postgres.PostgresDelegationStore;
import org.apache.james.user.postgres.PostgresUsersDAO;
import org.apache.james.user.postgres.PostgresUsersRepositoryConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

public class PostgresDelegationStoreTest implements DelegationStoreContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(TMailPostgresUserDataDefinition.MODULE);

    private PostgresUsersDAO postgresUsersDAO;
    private PostgresDelegationStore postgresDelegationStore;

    @BeforeEach
    void beforeEach() {
        postgresUsersDAO = new PostgresUsersDAO(postgresExtension.getDefaultPostgresExecutor(), PostgresUsersRepositoryConfiguration.DEFAULT);
        postgresDelegationStore = new PostgresDelegationStore(postgresUsersDAO, any -> Mono.just(true));
    }

    @Override
    public DelegationStore testee() {
        return postgresDelegationStore;
    }

    @Override
    public void addUser(Username username) {
        postgresUsersDAO.addUser(username, "password");
    }

    @Test
    void virtualUsersShouldNotBeListed() {
        postgresDelegationStore = new PostgresDelegationStore(postgresUsersDAO, any -> Mono.just(false));
        addUser(BOB);

        Mono.from(testee().addAuthorizedUser(ALICE).forUser(BOB)).block();

        assertThat(postgresUsersDAO.listReactive().collectList().block())
            .containsOnly(BOB);
    }
}
