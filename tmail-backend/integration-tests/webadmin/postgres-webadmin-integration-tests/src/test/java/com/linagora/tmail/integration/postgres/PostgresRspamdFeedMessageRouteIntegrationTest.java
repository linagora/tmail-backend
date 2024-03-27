package com.linagora.tmail.integration.postgres;

import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import com.linagora.tmail.rspamd.RspamdExtensionModule;
import com.linagora.tmail.integration.RspamdFeedMessageRouteIntegrationContract;

public class PostgresRspamdFeedMessageRouteIntegrationTest extends RspamdFeedMessageRouteIntegrationContract {

    @RegisterExtension
    static JamesServerExtension testExtension = PostgresWebAdminBase.JAMES_SERVER_EXTENSION_SUPPLIER.get()
        .extension(new RspamdExtensionModule())
        .build();
}
