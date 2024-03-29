package com.linagora.tmail.james;

import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_SUPPLIER;

import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.rfc8621.contract.MailboxGetMethodContract;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.uuid.Uuids;

public class PostgresMailboxGetMethodTest implements MailboxGetMethodContract {
    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_SUPPLIER.get().build();

    @Override
    public MailboxId randomMailboxId() {
        return PostgresMailboxId.of(Uuids.timeBased());
    }
}