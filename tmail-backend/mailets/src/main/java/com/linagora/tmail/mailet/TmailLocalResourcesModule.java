package com.linagora.tmail.mailet;

import org.apache.james.mailetcontainer.api.LocalResources;
import org.apache.james.mailetcontainer.impl.LocalResourcesImpl;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.linagora.tmail.team.TeamMailboxRepository;

public class TmailLocalResourcesModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(LocalResources.class).to(TmailLocalResources.class);
    }

    @Provides
    @Singleton
    TmailLocalResources localResources(LocalResourcesImpl localResources, TeamMailboxRepository teamMailboxRepository) {
        return new TmailLocalResources(localResources, teamMailboxRepository);
    }
}
