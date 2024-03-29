package com.linagora.tmail.james;

import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.common.LinagoraForwardSetMethodContract;

public class PostgresLinagoraForwardSetMethodTest implements LinagoraForwardSetMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = TmailJmapBase.JAMES_SERVER_EXTENSION_SUPPLIER.get()
        .build();
}