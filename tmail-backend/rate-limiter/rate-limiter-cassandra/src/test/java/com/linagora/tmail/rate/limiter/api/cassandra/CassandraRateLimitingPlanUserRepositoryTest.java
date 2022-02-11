package com.linagora.tmail.rate.limiter.api.cassandra;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.rate.limiter.api.RateLimitingPlanUserRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanUserRepositoryContract;
import com.linagora.tmail.rate.limiter.api.cassandra.dao.CassandraRateLimitPlanUserDAO;
import com.linagora.tmail.rate.limiter.api.cassandra.table.CassandraRateLimitPlanUserTable;

public class CassandraRateLimitingPlanUserRepositoryTest implements RateLimitingPlanUserRepositoryContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(CassandraRateLimitPlanUserTable.MODULE()));

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
