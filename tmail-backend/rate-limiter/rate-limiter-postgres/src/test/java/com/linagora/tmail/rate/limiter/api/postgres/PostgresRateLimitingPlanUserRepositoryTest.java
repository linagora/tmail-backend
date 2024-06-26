package com.linagora.tmail.rate.limiter.api.postgres;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.PostgresModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.rate.limiter.api.RateLimitingPlanUserRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanUserRepositoryContract;
import com.linagora.tmail.rate.limiter.api.postgres.repository.PostgresRateLimitingPlanUserRepository;
import com.linagora.tmail.rate.limiter.api.postgres.table.PostgresRateLimitPlanUserTable;

class PostgresRateLimitingPlanUserRepositoryTest implements RateLimitingPlanUserRepositoryContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(
        PostgresModule.aggregateModules(PostgresRateLimitPlanUserTable.MODULE()));

    private PostgresRateLimitingPlanUserRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresRateLimitingPlanUserRepository(postgresExtension.getExecutorFactory(), postgresExtension.getByPassRLSPostgresExecutor());
    }

    @Override
    public RateLimitingPlanUserRepository testee() {
        return repository;
    }
}
