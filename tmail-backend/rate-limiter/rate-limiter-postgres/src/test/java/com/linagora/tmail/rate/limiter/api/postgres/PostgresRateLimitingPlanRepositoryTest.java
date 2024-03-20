package com.linagora.tmail.rate.limiter.api.postgres;

import org.apache.james.backends.postgres.PostgresExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.rate.limiter.api.RateLimitingPlanRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanRepositoryContract;
import com.linagora.tmail.rate.limiter.api.postgres.dao.PostgresRateLimitingPlanDAO;
import com.linagora.tmail.rate.limiter.api.postgres.table.PostgresRateLimitPlanModule;

public class PostgresRateLimitingPlanRepositoryTest implements RateLimitingPlanRepositoryContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresRateLimitPlanModule.MODULE);

    @Override
    public RateLimitingPlanRepository testee() {
        return new PostgresRateLimitingPlanRepository(new PostgresRateLimitingPlanDAO(postgresExtension.getPostgresExecutor()));
    }
}
