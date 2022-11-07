package com.linagora.tmail.james.jmap.firebase;

import org.apache.james.jmap.method.Method;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.james.jmap.method.FirebaseCapabilitiesModule;
import com.linagora.tmail.james.jmap.method.FirebaseSubscriptionGetMethod;
import com.linagora.tmail.james.jmap.method.FirebaseSubscriptionSetMethod;

public class FirebaseCommonModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new FirebaseCapabilitiesModule());

        Multibinder.newSetBinder(binder(), Method.class)
            .addBinding()
            .to(FirebaseSubscriptionGetMethod.class);

        Multibinder.newSetBinder(binder(), Method.class)
            .addBinding()
            .to(FirebaseSubscriptionSetMethod.class);
    }
}
