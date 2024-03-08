package com.linagora.tmail.james.jmap.label;

import java.time.ZonedDateTime;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.postgres.change.PostgresStateFactory;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.jmap.firebase.FirebaseSubscriptionRepositoryContract;

class PostgresLabelChangeRepositoryTest implements LabelChangeRepositoryContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(
        PostgresModule.aggregateModules(PostgresLabelModule.MODULE));

    private PostgresLabelChangeRepository labelChangeRepository;

    @BeforeEach
    void setup() {
        UpdatableTickingClock clock = new UpdatableTickingClock(FirebaseSubscriptionRepositoryContract.NOW());
        labelChangeRepository = new PostgresLabelChangeRepository(postgresExtension.getExecutorFactory(), clock);
    }

    @Override
    public LabelChangeRepository testee() {
        return labelChangeRepository;
    }

    @Override
    public State.Factory stateFactory() {
        return new PostgresStateFactory();
    }

    @Override
    public void setClock(ZonedDateTime newTime) {

    }
}
