package com.linagora.tmail.james;

import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION;

import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.core.JmapRfc8621Configuration;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.common.LinagoraTicketAuthenticationContract;

public class PostgresTicketRoutesTest implements LinagoraTicketAuthenticationContract {

    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_FUNCTION
        .apply(binder -> binder.bind(JmapRfc8621Configuration.class)
            .toInstance(LinagoraTicketAuthenticationContract.jmapConfiguration()))
        .build();
}