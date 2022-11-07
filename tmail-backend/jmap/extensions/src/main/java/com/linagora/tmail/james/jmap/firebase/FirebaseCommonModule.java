package com.linagora.tmail.james.jmap.firebase;

import java.io.FileNotFoundException;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
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
import com.linagora.tmail.james.jmap.model.MissingOrInvalidFirebaseCredentialException;

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
    }

    @Provides
    @Singleton
    FirebaseConfiguration firebaseConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        Configuration configuration = propertiesProvider.getConfiguration("firebase");
        Optional<String> firebasePrivateKeyUrl = Optional.ofNullable(configuration.getString("privatekey.url"));
        return firebasePrivateKeyUrl.map(FirebaseConfiguration::new)
            .orElseThrow(() -> {
                LOGGER.error("Missing required `privatekey.url` declaration for Firebase configuration.");
                return new MissingOrInvalidFirebaseCredentialException("Missing required `privatekey.url` declaration for Firebase configuration.");
            });
    }
}
