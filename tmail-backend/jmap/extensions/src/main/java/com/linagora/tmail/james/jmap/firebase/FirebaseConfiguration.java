package com.linagora.tmail.james.jmap.firebase;

import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.james.jmap.model.MissingOrInvalidFirebaseCredentialException;

public record FirebaseConfiguration(String firebasePrivateKeyUrl,
                                    Optional<String> apiKey,
                                    Optional<String> appId,
                                    Optional<String> messagingSenderId,
                                    Optional<String> projectId,
                                    Optional<String> databaseUrl,
                                    Optional<String> storageBucket,
                                    Optional<String> authDomain,
                                    Optional<String> vapidPublicKey) {
    private static final Logger LOGGER = LoggerFactory.getLogger(FirebaseConfiguration.class);

    private static final String PRIVATE_KEY_URL_PROPERTY = "privatekey.url";
    private static final String API_KEY_PROPERTY = "api.key";
    private static final String APP_ID_PROPERTY = "app.id";
    private static final String MESSAGING_SENDER_ID_PROPERTY = "messaging.sender.id";
    private static final String PROJECT_ID_PROPERTY = "project.id";
    private static final String DATABASE_URL_PROPERTY = "database.url";
    private static final String STORAGE_BUCKET_PROPERTY = "storage.bucket";
    private static final String AUTH_DOMAIN_PROPERTY = "auth.domain";
    private static final String VAPID_PUBLIC_KEY_PROPERTY = "vapid.public.key";

    public static FirebaseConfiguration from(Configuration configuration) {
        String firebasePrivateKeyUrl = Optional.ofNullable(configuration.getString(PRIVATE_KEY_URL_PROPERTY))
            .orElseThrow(() -> {
                LOGGER.error("Missing required `privatekey.url` declaration for Firebase configuration.");
                return new MissingOrInvalidFirebaseCredentialException("Missing required `privatekey.url` declaration for Firebase configuration.");
            });

        Optional<String> apiKey = Optional.ofNullable(configuration.getString(API_KEY_PROPERTY));
        Optional<String> appId = Optional.ofNullable(configuration.getString(APP_ID_PROPERTY));
        Optional<String> messagingSenderId = Optional.ofNullable(configuration.getString(MESSAGING_SENDER_ID_PROPERTY));
        Optional<String> projectId = Optional.ofNullable(configuration.getString(PROJECT_ID_PROPERTY));
        Optional<String> databaseUrl = Optional.ofNullable(configuration.getString(DATABASE_URL_PROPERTY));
        Optional<String> storageBucket = Optional.ofNullable(configuration.getString(STORAGE_BUCKET_PROPERTY));
        Optional<String> authDomain = Optional.ofNullable(configuration.getString(AUTH_DOMAIN_PROPERTY));
        Optional<String> vapidPublicKey = Optional.ofNullable(configuration.getString(VAPID_PUBLIC_KEY_PROPERTY));

        return new FirebaseConfiguration(firebasePrivateKeyUrl, apiKey, appId, messagingSenderId, projectId, databaseUrl,
            storageBucket, authDomain, vapidPublicKey);
    }
}
