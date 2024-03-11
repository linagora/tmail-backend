package com.linagora.tmail.james.jmap.label;

import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.user.api.UsernameChangeTaskStep;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class PostgresLabelRepositoryModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder<PostgresModule> postgresDataDefinitions = Multibinder.newSetBinder(binder(), PostgresModule.class);
        postgresDataDefinitions.addBinding().toInstance(PostgresLabelModule.MODULE);

        bind(LabelRepository.class).to(PostgresLabelRepository.class);
        bind(PostgresLabelRepository.class).in(Scopes.SINGLETON);
        bind(LabelChangeRepository.class).to(PostgresLabelChangeRepository.class);
        bind(PostgresLabelChangeRepository.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), UsernameChangeTaskStep.class).addBinding().to(LabelUsernameChangeTaskStep.class);
        Multibinder.newSetBinder(binder(), DeleteUserDataTaskStep.class).addBinding().to(LabelUserDeletionTaskStep.class);
    }
}