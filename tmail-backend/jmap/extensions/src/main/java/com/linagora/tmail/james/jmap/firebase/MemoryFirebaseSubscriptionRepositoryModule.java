package com.linagora.tmail.james.jmap.firebase;

import org.apache.james.user.api.DeleteUserDataTaskStep;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class MemoryFirebaseSubscriptionRepositoryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(MemoryFirebaseSubscriptionRepository.class).in(Scopes.SINGLETON);
        bind(FirebaseSubscriptionRepository.class).to(MemoryFirebaseSubscriptionRepository.class);

        Multibinder.newSetBinder(binder(), DeleteUserDataTaskStep.class)
            .addBinding()
            .to(FirebaseSubscriptionUserDeletionTaskStep.class);
    }
}
