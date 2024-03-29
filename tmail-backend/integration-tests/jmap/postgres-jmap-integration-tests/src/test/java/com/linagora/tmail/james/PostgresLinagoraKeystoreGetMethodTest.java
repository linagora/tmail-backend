package com.linagora.tmail.james;

import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION;

import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.common.LinagoraKeystoreGetMethodContract;
import com.linagora.tmail.james.common.module.JmapGuiceKeystoreManagerModule;

public class PostgresLinagoraKeystoreGetMethodTest implements LinagoraKeystoreGetMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_FUNCTION
        .apply(new JmapGuiceKeystoreManagerModule())
        .build();
}