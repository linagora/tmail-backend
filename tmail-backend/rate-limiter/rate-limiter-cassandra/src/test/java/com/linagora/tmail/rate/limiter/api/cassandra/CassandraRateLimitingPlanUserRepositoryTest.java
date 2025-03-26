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

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.rate.limiter.api.RateLimitingPlanUserRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanUserRepositoryContract;
import com.linagora.tmail.rate.limiter.api.cassandra.dao.CassandraRateLimitPlanUserDAO;
import com.linagora.tmail.rate.limiter.api.cassandra.table.CassandraRateLimitPlanUserTable;

public class CassandraRateLimitingPlanUserRepositoryTest implements RateLimitingPlanUserRepositoryContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraDataDefinition.aggregateModules(CassandraRateLimitPlanUserTable.MODULE()));

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
}
