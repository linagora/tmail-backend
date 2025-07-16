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

package com.linagora.tmail.rate.limiter.api.cassandra;

import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.CqlSession;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanUserRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanUserRepositoryContract;
import com.linagora.tmail.rate.limiter.api.cassandra.dao.CassandraRateLimitPlanUserDAO;
import com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition;

import reactor.core.publisher.Mono;

public class CassandraRateLimitingPlanUserRepositoryTest implements RateLimitingPlanUserRepositoryContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraDataDefinition.aggregateModules(TMailCassandraUsersRepositoryDataDefinition.MODULE));

    private CassandraRateLimitingPlanUserRepository repository;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        CassandraRateLimitPlanUserDAO dao = new CassandraRateLimitPlanUserDAO(cassandra.getConf());
        repository = new CassandraRateLimitingPlanUserRepository(dao);
    }

    @Override
    public RateLimitingPlanUserRepository testee() {
        return repository;
    }

    @Test
    void shouldNotDeleteUserRecordWhenRevokePlanId(CassandraCluster cassandraCluster) {
        CqlSession testingSession = cassandraCluster.getConf();

        // Given the Bob record in the user table
        testingSession.execute(String.format("INSERT INTO user (name, realname) VALUES ('%s', '%s')",  BOB.asString(), BOB.getLocalPart()));

        // Attach rate limiting plan for Bob
        Mono.from(repository.applyPlan(BOB, RateLimitingPlanUserRepositoryContract.PLAN_ID_1())).block();

        // Revoke Bob plan (e.g. as part of username change process)
        Mono.from(repository.revokePlan(BOB)).block();

        // Assert that the user record still exists after revoking plan, so other user associated data is not lost
        assertThat(testingSession.execute(String.format("SELECT * FROM user WHERE name = '%s'",  BOB.asString()))
            .iterator()
            .next()
            .get("realname", String.class))
            .isEqualTo(BOB.getLocalPart());
    }
}
