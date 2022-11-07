package com.linagora.tmail.james.jmap.firebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.linagora.tmail.james.jmap.model.FirebaseToken;
import com.linagora.tmail.james.jmap.model.MissingOrInvalidFirebaseCredentialException;

@Disabled("manual test suite")
class FirebaseClientTest {
    public static final FirebaseToken VALID_TOKEN = new FirebaseToken(Optional.ofNullable(System.getenv("FIREBASE_TOKEN"))
        .orElse("change_me"));
    public static final FirebaseToken INVALID_TOKEN = new FirebaseToken("invalid_token");
    public static final String VALID_KEY_URL = Optional.ofNullable(System.getenv("FIREBASE_KEY_URL"))
        .orElse("src/test/resources/valid-firebase-private-key.json");
    public static final String INVALID_KEY_URL = "src/test/resources/invalid-firebase-private-key.json";
    public static final String NOT_FOUND_KEY_URL = "src/test/resources/notFound.json";
    public static final FirebasePushRequest VALID_PUSH_REQUEST = new FirebasePushRequest(Map.of("a3123:Email", "state1"), VALID_TOKEN);
    public static final FirebasePushRequest INVALID_PUSH_REQUEST = new FirebasePushRequest(Map.of("a3123:Email", "state1"), INVALID_TOKEN);

    private static FirebasePushClient firebasePushClient;

    @BeforeAll
    static void setup() {
        firebasePushClient = new FirebasePushClient(new FirebaseConfiguration(VALID_KEY_URL));
    }

    @Test
    void pushWithValidTokenShouldPushData() {
        assertThatCode(() -> firebasePushClient.push(VALID_PUSH_REQUEST).block())
            .doesNotThrowAnyException();
        // Notification popup should show up
    }

    @Test
    void pushWithInvalidTokenShouldThrowException() {
        assertThatCode(() -> firebasePushClient.push(INVALID_PUSH_REQUEST).block())
            .cause()
            .isInstanceOf(FirebaseMessagingException.class)
            .hasMessage("The registration token is not a valid FCM registration token");
    }

    @Test
    void validateValidTokenShouldSucceedAndPushNoMessage() {
        assertThat(firebasePushClient.validateToken(VALID_TOKEN).block())
            .isTrue();

        // Notification popup should not show up
    }

    @Test
    void validateInvalidTokenShouldReturnFalse() {
        assertThat(firebasePushClient.validateToken(INVALID_TOKEN).block())
            .isFalse();
    }

    @Test
    void notFoundFirebasePrivateKeyShouldThrowError() {
        FirebaseConfiguration firebaseConfiguration = new FirebaseConfiguration(NOT_FOUND_KEY_URL);

        assertThatCode(() -> new FirebasePushClient(firebaseConfiguration))
            .isInstanceOf(MissingOrInvalidFirebaseCredentialException.class);
    }

    @Test
    void invalidFirebasePrivateKeyShouldThrowError() {
        FirebaseConfiguration firebaseConfiguration = new FirebaseConfiguration(INVALID_KEY_URL);

        assertThatCode(() -> new FirebasePushClient(firebaseConfiguration))
            .isInstanceOf(MissingOrInvalidFirebaseCredentialException.class);
    }

}
