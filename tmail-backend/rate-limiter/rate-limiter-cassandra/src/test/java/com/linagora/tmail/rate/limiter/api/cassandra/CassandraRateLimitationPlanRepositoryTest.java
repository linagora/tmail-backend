package com.linagora.tmail.rate.limiter.api.cassandra;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.rate.limiter.api.RateLimitationPlanRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitationPlanRepositoryContract;
import com.linagora.tmail.rate.limiter.api.cassandra.dao.CassandraRateLimitPlanDAO;
import com.linagora.tmail.rate.limiter.api.cassandra.table.CassandraRateLimitPlanTable;

public class CassandraRateLimitationPlanRepositoryTest implements RateLimitationPlanRepositoryContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(CassandraRateLimitPlanTable.MODULE(), CassandraSchemaVersionModule.MODULE));

    private CassandraRateLimitationPlanRepository repository;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        CassandraRateLimitPlanDAO dao = new CassandraRateLimitPlanDAO(cassandra.getConf());
        repository = new CassandraRateLimitationPlanRepository(dao);
    }

    @Override
    public RateLimitationPlanRepository testee() {
        return repository;
    }
}
