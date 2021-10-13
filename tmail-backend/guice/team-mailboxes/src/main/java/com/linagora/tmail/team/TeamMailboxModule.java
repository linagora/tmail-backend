package com.linagora.tmail.team;

import org.apache.james.UserEntityValidator;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.utils.GuiceProbe;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class TeamMailboxModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(TeamMailboxRepositoryImpl.class).in(Scopes.SINGLETON);
        bind(TMailCanSendFrom.class).in(Scopes.SINGLETON);

        bind(TeamMailboxRepository.class).to(TeamMailboxRepositoryImpl.class);
        bind(CanSendFrom.class).to(TMailCanSendFrom.class);

        Multibinder.newSetBinder(binder(), GuiceProbe.class)
            .addBinding()
            .to(TeamMailboxProbe.class);

        Multibinder.newSetBinder(binder(), UserEntityValidator.class)
            .addBinding()
            .to(TeamMailboxUserEntityValidator.class);
    }
}
