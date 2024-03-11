package com.linagora.tmail.james.jmap.firebase;

import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.user.api.DeleteUserDataTaskStep;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class PostgresFirebaseRepositoryModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder<PostgresModule> postgresDataDefinitions = Multibinder.newSetBinder(binder(), PostgresModule.class);
        postgresDataDefinitions.addBinding().toInstance(PostgresFirebaseModule.MODULE);

        bind(FirebaseSubscriptionRepository.class).to(PostgresFirebaseSubscriptionRepository.class);
        bind(PostgresFirebaseSubscriptionRepository.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), DeleteUserDataTaskStep.class).addBinding().to(FirebaseSubscriptionUserDeletionTaskStep.class);
    }
}