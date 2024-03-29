package com.linagora.tmail.james;

import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION;

import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.AbstractModule;
import com.linagora.tmail.james.common.FirebaseSubscriptionGetMethodContract;
import com.linagora.tmail.james.common.FirebaseSubscriptionProbeModule;
import com.linagora.tmail.james.jmap.firebase.FirebasePushClient;

public class PostgresFirebaseSubscriptionGetMethodTest implements FirebaseSubscriptionGetMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_FUNCTION
        .apply(new AbstractModule() {
            @Override
            protected void configure() {
                install(new FirebaseSubscriptionProbeModule());
                bind(FirebasePushClient.class).toInstance(FirebaseSubscriptionGetMethodContract.firebasePushClient());
            }
        })
        .build();
}