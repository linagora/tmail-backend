package com.linagora.tmail.james;

import static com.linagora.tmail.james.PostgresFirebaseSubscriptionGetMethodTest.FIREBASE_TEST_MODULE;

import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.common.LinagoraEchoMethodContract;

public class PostgresLinagoraEchoMethodTest implements LinagoraEchoMethodContract {
    @RegisterExtension
    static JamesServerExtension testExtension = TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION
        .apply(FIREBASE_TEST_MODULE)
        .build();
}
