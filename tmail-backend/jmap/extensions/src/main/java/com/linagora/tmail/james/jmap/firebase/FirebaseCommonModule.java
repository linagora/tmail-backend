package com.linagora.tmail.james.jmap.firebase;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.events.EventListener;
import org.apache.james.jmap.method.Method;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.james.jmap.method.FirebaseCapabilitiesModule;
import com.linagora.tmail.james.jmap.method.FirebaseSubscriptionGetMethod;
import com.linagora.tmail.james.jmap.method.FirebaseSubscriptionSetMethod;

public class FirebaseCommonModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(FirebaseCommonModule.class);

    @Override
    protected void configure() {
        install(new FirebaseCapabilitiesModule());

        Multibinder.newSetBinder(binder(), Method.class)
            .addBinding()
            .to(FirebaseSubscriptionGetMethod.class);

        Multibinder.newSetBinder(binder(), Method.class)
            .addBinding()
            .to(FirebaseSubscriptionSetMethod.class);

        bind(FirebasePushClient.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class)
            .addBinding().to(FirebasePushListener.class);
    }

    @Provides
    @Singleton
    FirebaseConfiguration firebaseConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        Configuration configuration = propertiesProvider.getConfiguration("firebase");
        return FirebaseConfiguration.from(configuration);
    }
}
