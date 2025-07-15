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

package com.linagora.tmail.user.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Username;
import org.apache.james.user.api.DelegationStore;
import org.apache.james.user.api.DelegationStoreContract;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.cassandra.CassandraDelegationStore;
import org.apache.james.user.cassandra.CassandraUsersDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

public class CassandraDelegationStoreTest implements DelegationStoreContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(TMailCassandraUsersRepositoryDataDefinition.MODULE);

    private CassandraDelegationStore testee;
    private CassandraUsersDAO cassandraUsersDAO;

    @BeforeEach
    void setUp() {
        cassandraUsersDAO = new CassandraUsersDAO(cassandraCluster.getCassandraCluster().getConf());
        testee = new CassandraDelegationStore(cassandraUsersDAO, any -> Mono.just(true));
    }

    @Override
    public DelegationStore testee() {
        return testee;
    }

    @Override
    public void addUser(Username username) {
        try {
            cassandraUsersDAO.addUser(username, "password");
        } catch (UsersRepositoryException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void virtualUsersShouldNotBeListed() {
        testee = new CassandraDelegationStore(cassandraUsersDAO, any -> Mono.just(false));
        addUser(BOB);

        Mono.from(testee().addAuthorizedUser(ALICE).forUser(BOB)).block();

        assertThat(cassandraUsersDAO.listReactive().collectList().block())
            .containsOnly(BOB);
    }
}
