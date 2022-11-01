package com.linagora.tmail.james.jmap.firebase;

import com.google.inject.AbstractModule;
import com.linagora.tmail.james.jmap.method.FirebaseCapabilitiesModule;
import com.linagora.tmail.james.jmap.method.FirebaseSubscriptionGetMethodModule;

public class FirebaseCommonModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new FirebaseCapabilitiesModule());
        install(new FirebaseSubscriptionGetMethodModule());
        install(new MemoryFirebaseSubscriptionModule());
    }
}
