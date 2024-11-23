package com.linagora.tmail.james.jmap.settings;

import org.apache.james.backends.postgres.PostgresExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresJmapSettingsRepositoryTest implements JmapSettingsRepositoryContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresJmapSettingsModule.MODULE);

    @Override
    public JmapSettingsRepository testee() {
        return new PostgresJmapSettingsRepository(new PostgresJmapSettingsDAO.Factory(postgresExtension.getExecutorFactory()));
    }
}
