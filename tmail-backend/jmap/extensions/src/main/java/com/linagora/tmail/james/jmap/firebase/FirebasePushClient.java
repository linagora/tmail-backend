package com.linagora.tmail.james.jmap.firebase;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.linagora.tmail.james.jmap.model.FirebaseToken;
import com.linagora.tmail.james.jmap.model.MissingOrInvalidFirebaseCredentialException;

import reactor.core.publisher.Mono;

public class FirebasePushClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(FirebasePushClient.class);
    private static final Boolean DRY_RUN = true;

    private final FirebaseMessaging firebaseMessaging;

    @Inject
    public FirebasePushClient(FirebaseConfiguration configuration) {
        try {
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(new FileInputStream(configuration.firebasePrivateKeyUrl())))
                .build();

            FirebaseApp.initializeApp(options);
        } catch (FileNotFoundException e) {
            LOGGER.error("Declared private key file for Firebase is not found");
            throw new MissingOrInvalidFirebaseCredentialException(e.getMessage());
        } catch (IOException e) {
            LOGGER.error("Declared private key file for Firebase is invalid");
            throw new MissingOrInvalidFirebaseCredentialException(e.getMessage());
        }

        this.firebaseMessaging = FirebaseMessaging.getInstance();
    }

    public Mono<Void> push(FirebasePushRequest pushRequest) {
        return sendReactive(createFcmMessage(pushRequest), !DRY_RUN);
    }

    public Mono<Boolean> validateToken(FirebaseToken token) {
        return sendReactive(createEmptyMessage(token), DRY_RUN)
            .thenReturn(true)
            .onErrorResume(FirebaseMessagingException.class, e -> {
                if (e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT
                    || e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    return Mono.just(false);
                }
                return Mono.error(e);
            });
    }

    private Message createFcmMessage(FirebasePushRequest pushRequest) {
        return Message.builder()
            .putAllData(pushRequest.stateChangesMap())
            .setToken(pushRequest.token().value())
            .build();
    }

    private Message createEmptyMessage(FirebaseToken token) {
        return Message.builder()
            .setToken(token.value())
            .build();
    }

    private Mono<Void> sendReactive(Message fcmMessage, boolean dryRun) {
        return Mono.create(sink -> {
            ApiFuture<String> apiFuture = firebaseMessaging.sendAsync(fcmMessage, dryRun);
            apiFuture.addListener(() -> {
                try {
                    apiFuture.get();
                    sink.success();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    sink.error(e.getCause());
                }
            }, MoreExecutors.directExecutor());
        });
    }
}
