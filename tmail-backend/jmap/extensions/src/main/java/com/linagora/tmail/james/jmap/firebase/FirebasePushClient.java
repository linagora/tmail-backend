/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.jmap.firebase;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;

import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.WebpushConfig;
import com.linagora.tmail.james.jmap.model.FirebaseToken;
import com.linagora.tmail.james.jmap.model.MissingOrInvalidFirebaseCredentialException;

import reactor.core.publisher.Mono;

public class FirebasePushClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(FirebasePushClient.class);
    private static final Boolean DRY_RUN = true;
    private static final String APNS_URGENCY_HEADER = "apns-priority";
    private static final String WEB_PUSH_URGENCY_HEADER = "Urgency";
    private static final String APNS_REQUIRED_NORMAL_PRIORITY = "5";

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
        return sendReactive(createFcmMessage(pushRequest), !DRY_RUN)
            .doOnEach(ReactorUtils.logOnError(throwable -> LOGGER.warn("Error when pushing FCM notification", throwable)));
    }

    public Mono<Boolean> validateToken(FirebaseToken token) {
        return sendReactive(createEmptyMessage(token), DRY_RUN)
            .thenReturn(true)
            .onErrorResume(FirebaseMessagingException.class, e -> {
                LOGGER.info("Invalid token", e);
                if (e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT
                    || e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    return Mono.just(false);
                }
                return Mono.error(e);
            });
    }

    private Message createFcmMessage(FirebasePushRequest pushRequest) {
        if (pushRequest.urgency().equals(FirebasePushUrgency.NORMAL)) {
            return Message.builder()
                .putAllData(pushRequest.stateChangesMap())
                .setToken(pushRequest.token().value())
                .setAndroidConfig(AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.NORMAL)
                    .build())
                .setApnsConfig(buildApnsConfig(pushRequest))
                .setWebpushConfig(WebpushConfig.builder()
                    .putHeader(WEB_PUSH_URGENCY_HEADER, "normal")
                    .build())
                .build();
        }
        return Message.builder()
            .putAllData(pushRequest.stateChangesMap())
            .setToken(pushRequest.token().value())
            .setAndroidConfig(AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .build())
            .setApnsConfig(buildApnsConfig(pushRequest))
            .setWebpushConfig(WebpushConfig.builder()
                .putHeader(WEB_PUSH_URGENCY_HEADER, "high")
                .build())
            .build();
    }

    private ApnsConfig buildApnsConfig(FirebasePushRequest pushRequest) {
        return ApnsConfig.builder()
            .putHeader(APNS_URGENCY_HEADER, APNS_REQUIRED_NORMAL_PRIORITY)
            .setAps(buildApsDictionary(pushRequest))
            .build();
    }

    private Aps buildApsDictionary(FirebasePushRequest pushRequest) {
        if (containsEmailDelivery(pushRequest)) {
            return Aps.builder()
                .setAlert(ApsAlert.builder()
                    .setTitle("Twake Mail")
                    .setBody("You have new message")
                    .build())
                .setMutableContent(true)
                .build();
        }
        return Aps.builder()
            .setContentAvailable(true)
            .build();
    }

    private boolean containsEmailDelivery(FirebasePushRequest pushRequest) {
        return pushRequest.urgency().equals(FirebasePushUrgency.HIGH);
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
