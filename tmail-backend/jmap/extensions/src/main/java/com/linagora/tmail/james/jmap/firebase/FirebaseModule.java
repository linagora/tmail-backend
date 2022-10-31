package com.linagora.tmail.james.jmap.firebase;

import com.google.inject.AbstractModule;
import com.linagora.tmail.james.jmap.method.FirebaseCapabilitiesModule;

public class FirebaseModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new FirebaseCapabilitiesModule());
        // JMAP Firebase method binding ...
    }
}
