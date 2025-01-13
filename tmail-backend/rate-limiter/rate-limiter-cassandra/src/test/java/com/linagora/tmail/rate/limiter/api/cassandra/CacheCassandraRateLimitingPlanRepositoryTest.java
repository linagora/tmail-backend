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

import java.time.Duration;
import java.util.Optional;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.rate.limiter.api.CacheRateLimitingPlan;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanRepositoryContract;
import com.linagora.tmail.rate.limiter.api.cassandra.dao.CassandraRateLimitPlanDAO;
import com.linagora.tmail.rate.limiter.api.cassandra.table.CassandraRateLimitPlanTable;

import scala.jdk.javaapi.OptionConverters;

public class CacheCassandraRateLimitingPlanRepositoryTest implements RateLimitingPlanRepositoryContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(CassandraRateLimitPlanTable.MODULE(), CassandraSchemaVersionModule.MODULE));

    private CacheRateLimitingPlan repository;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        CassandraRateLimitPlanDAO dao = new CassandraRateLimitPlanDAO(cassandra.getConf());
        RateLimitingPlanRepository rateLimitingPlanRepository = new CassandraRateLimitingPlanRepository(dao);
        repository = new CacheRateLimitingPlan(rateLimitingPlanRepository, Duration.ofMinutes(2), new NoopGaugeRegistry(), OptionConverters.toScala(Optional.empty()));
    }

    @Override
    public RateLimitingPlanRepository testee() {
        return repository;
    }
}
