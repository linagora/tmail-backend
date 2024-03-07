package com.linagora.tmail.james.jmap.firebase;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.jmap.api.change.TypeStateFactory;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import scala.jdk.javaapi.CollectionConverters;

class PostgresFirebaseSubscriptionRepositoryTest implements FirebaseSubscriptionRepositoryContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(
        PostgresModule.aggregateModules(PostgresFirebaseModule.MODULE));

    private UpdatableTickingClock clock;
    private FirebaseSubscriptionRepository firebaseSubscriptionRepository;

    @BeforeEach
    void setup() {
        clock = new UpdatableTickingClock(FirebaseSubscriptionRepositoryContract.NOW());
        firebaseSubscriptionRepository = new PostgresFirebaseSubscriptionRepository(clock,
            new TypeStateFactory(CollectionConverters.asJava(FirebaseSubscriptionRepositoryContract.TYPE_NAME_SET())),
            postgresExtension.getExecutorFactory());
    }

    @Override
    public UpdatableTickingClock clock() {
        return clock;
    }

    @Override
    public FirebaseSubscriptionRepository testee() {
        return firebaseSubscriptionRepository;
    }
}
