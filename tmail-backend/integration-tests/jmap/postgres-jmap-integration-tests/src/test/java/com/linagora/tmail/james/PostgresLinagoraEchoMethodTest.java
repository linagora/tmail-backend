package com.linagora.tmail.james;

import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.AbstractModule;
import com.linagora.tmail.james.common.FirebaseSubscriptionProbeModule;
import com.linagora.tmail.james.common.LinagoraEchoMethodContract;
import com.linagora.tmail.james.jmap.firebase.FirebasePushClient;

public class PostgresLinagoraEchoMethodTest implements LinagoraEchoMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION
        .apply(new AbstractModule() {
            @Override
            protected void configure() {
                install(new FirebaseSubscriptionProbeModule());
                bind(FirebasePushClient.class).toInstance(LinagoraEchoMethodContract.firebasePushClient());
            }
        })
        .build();
}