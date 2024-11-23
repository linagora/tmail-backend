package com.linagora.tmail.james.jmap.settings;

import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.user.api.UsernameChangeTaskStep;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class PostgresJmapSettingsRepositoryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(JmapSettingsRepository.class).to(PostgresJmapSettingsRepository.class);
        bind(PostgresJmapSettingsRepository.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), UsernameChangeTaskStep.class).addBinding().to(JmapSettingsUsernameChangeTaskStep.class);
        Multibinder.newSetBinder(binder(), DeleteUserDataTaskStep.class).addBinding().to(JmapSettingsUserDeletionTaskStep.class);

        Multibinder<PostgresModule> postgresDataDefinitions = Multibinder.newSetBinder(binder(), PostgresModule.class);
        postgresDataDefinitions.addBinding().toInstance(PostgresJmapSettingsModule.MODULE);
    }
}
