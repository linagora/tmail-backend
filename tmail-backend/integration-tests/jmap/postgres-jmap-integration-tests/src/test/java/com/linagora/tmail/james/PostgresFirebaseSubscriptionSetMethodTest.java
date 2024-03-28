package com.linagora.tmail.james;

import static com.linagora.tmail.james.PostgresFirebaseSubscriptionGetMethodTest.FIREBASE_TEST_MODULE;
import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION;

import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.common.FirebaseSubscriptionSetMethodContract;

public class PostgresFirebaseSubscriptionSetMethodTest implements FirebaseSubscriptionSetMethodContract {
    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_FUNCTION.apply(FIREBASE_TEST_MODULE)
        .build();
}
