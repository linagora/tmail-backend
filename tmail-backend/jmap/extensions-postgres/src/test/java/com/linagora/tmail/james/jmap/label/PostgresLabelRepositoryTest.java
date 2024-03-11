package com.linagora.tmail.james.jmap.label;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.PostgresModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgresLabelRepositoryTest implements LabelRepositoryContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(
        PostgresModule.aggregateModules(PostgresLabelModule.MODULE));

    private PostgresLabelRepository labelRepository;

    @BeforeEach
    void setUp() {
        labelRepository = new PostgresLabelRepository(postgresExtension.getExecutorFactory());
    }

    @Override
    public LabelRepository testee() {
        return labelRepository;
    }
}
