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

package com.linagora.tmail.rate.limiter.api.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.CqlSession;
import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitingRepositoryContract;
import com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition;

import reactor.core.publisher.Mono;

public class CassandraRateLimitingRepositoryTest implements RateLimitingRepositoryContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(TMailCassandraUsersRepositoryDataDefinition.MODULE);

    private CassandraRateLimitingRepository cassandraRateLimitingRepository;

    @BeforeEach
    void setUp(CassandraCluster cassandraCluster) {
        cassandraRateLimitingRepository = new CassandraRateLimitingRepository(cassandraCluster.getConf());
    }

    @Override
    public RateLimitingRepository testee() {
        return cassandraRateLimitingRepository;
    }

    @Test
    void shouldNotDeleteUserRecordWhenRevokeRateLimiting(CassandraCluster cassandraCluster) {
        CqlSession testingSession = cassandraCluster.getConf();

        // Given the Bob record in the user table
        testingSession.execute(String.format("INSERT INTO user (name, realname) VALUES ('%s', '%s')",  BOB.asString(), BOB.getLocalPart()));

        // Set Bob rate limits
        Mono.from(cassandraRateLimitingRepository.setRateLimiting(BOB, RATE_LIMITING_1)).block();

        // Revoke Bob rate limits
        Mono.from(cassandraRateLimitingRepository.revokeRateLimiting(BOB)).block();

        // Assert that the user record still exists after revoking rate limits, so other user associated data is not lost
        assertThat(testingSession.execute(String.format("SELECT * FROM user WHERE name = '%s'",  BOB.asString()))
            .iterator()
            .next()
            .get("realname", String.class))
            .isEqualTo(BOB.getLocalPart());
    }
}
