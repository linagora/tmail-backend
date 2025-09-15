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

package com.linagora.tmail.saas.api.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.CqlSession;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.api.SaaSAccountRepositoryContract;

import reactor.core.publisher.Mono;

public class CassandraSaaSAccountRepositoryTest implements SaaSAccountRepositoryContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraSaaSDataDefinition.MODULE);

    private CassandraSaaSAccountRepository cassandraSaaSAccountRepository;

    @BeforeEach
    void setUp(CassandraCluster cassandraCluster) {
        cassandraSaaSAccountRepository = new CassandraSaaSAccountRepository(cassandraCluster.getConf());
    }

    @Override
    public SaaSAccountRepository testee() {
        return cassandraSaaSAccountRepository;
    }

    @Test
    void shouldNotDeleteUserRecordWhenDeleteSaaSAccount(CassandraCluster cassandraCluster) {
        CqlSession testingSession = cassandraCluster.getConf();

        // Given the Bob record in the user table
        testingSession.execute(String.format("INSERT INTO user (name, realname) VALUES ('%s', '%s')",  BOB.asString(), BOB.getLocalPart()));

        // Set Bob saas account
        Mono.from(cassandraSaaSAccountRepository.upsertSaasAccount(BOB, SAAS_ACCOUNT)).block();

        // Delete saas account
        Mono.from(cassandraSaaSAccountRepository.deleteSaaSAccount(BOB)).block();

        // Assert that the user record still exists after delete saas account, so other user associated data is not lost
        assertThat(testingSession.execute(String.format("SELECT * FROM user WHERE name = '%s'",  BOB.asString()))
            .iterator()
            .next()
            .get("realname", String.class))
            .isEqualTo(BOB.getLocalPart());
    }
}
