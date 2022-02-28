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
import com.linagora.tmail.rate.limiter.api.RateLimitationPlanRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitationPlanRepositoryContract;
import com.linagora.tmail.rate.limiter.api.cassandra.dao.CassandraRateLimitPlanDAO;
import com.linagora.tmail.rate.limiter.api.cassandra.table.CassandraRateLimitPlanTable;

import scala.jdk.javaapi.OptionConverters;

public class CacheCassandraRateLimitingPlanRepositoryTest implements RateLimitationPlanRepositoryContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(CassandraRateLimitPlanTable.MODULE(), CassandraSchemaVersionModule.MODULE));

    private CacheRateLimitingPlan repository;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        CassandraRateLimitPlanDAO dao = new CassandraRateLimitPlanDAO(cassandra.getConf());
        RateLimitationPlanRepository rateLimitationPlanRepository = new CassandraRateLimitationPlanRepository(dao);
        repository = new CacheRateLimitingPlan(rateLimitationPlanRepository, Duration.ofMinutes(2), new NoopGaugeRegistry(), OptionConverters.toScala(Optional.empty()));
    }

    @Override
    public RateLimitationPlanRepository testee() {
        return repository;
    }
}
