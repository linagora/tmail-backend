package com.linagora.tmail.james.jmap.firebase;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class MemoryFirebaseSubscriptionModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(MemoryFirebaseSubscriptionRepository.class).in(Scopes.SINGLETON);
        bind(FirebaseSubscriptionRepository.class).to(MemoryFirebaseSubscriptionRepository.class);
    }
}
