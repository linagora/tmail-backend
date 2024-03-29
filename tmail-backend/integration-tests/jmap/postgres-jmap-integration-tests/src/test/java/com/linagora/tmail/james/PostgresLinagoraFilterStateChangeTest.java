package com.linagora.tmail.james;

import org.apache.james.JamesServerExtension;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.common.LinagoraFilterStateChangeTest;

public class PostgresLinagoraFilterStateChangeTest implements LinagoraFilterStateChangeTest {

    @RegisterExtension
    static JamesServerExtension testExtension = TmailJmapBase.JAMES_SERVER_EXTENSION_SUPPLIER.get()
        .build();

    @Override
    public String generateMailboxIdForUser() {
        return PostgresMailboxId.of("123e4567-e89b-12d3-a456-426614174000").asUuid().toString();
    }

    @Override
    public String generateAccountIdAsString() {
        return "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6";
    }
}