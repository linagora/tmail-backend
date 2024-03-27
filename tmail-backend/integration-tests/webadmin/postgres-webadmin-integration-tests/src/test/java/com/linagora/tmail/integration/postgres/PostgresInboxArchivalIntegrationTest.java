package com.linagora.tmail.integration.postgres;

import static com.linagora.tmail.integration.postgres.PostgresWebAdminBase.JAMES_SERVER_EXTENSION_SUPPLIER;

import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.integration.InboxArchivalIntegrationContract;

public class PostgresInboxArchivalIntegrationTest extends InboxArchivalIntegrationContract {

    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_SUPPLIER.get().build();
}
