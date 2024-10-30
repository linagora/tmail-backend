package com.linagora.tmail.james.jmap.ticket;

import java.time.Clock;

import org.apache.james.backends.postgres.PostgresExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgresTicketStoreTest implements TicketStoreContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresTicketStore.MODULE());

    private PostgresTicketStore testee;

    @BeforeEach
    void setup() {
        testee = new PostgresTicketStore(postgresExtension.getDefaultPostgresExecutor(), Clock.systemUTC());
    }

    @Override
    public TicketStore testee() {
        return testee;
    }
}
