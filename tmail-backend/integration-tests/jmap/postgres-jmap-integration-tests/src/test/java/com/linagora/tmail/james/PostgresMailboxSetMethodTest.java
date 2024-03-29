package com.linagora.tmail.james;

import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_SUPPLIER;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.rfc8621.contract.MailboxSetMethodContract;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.uuid.Uuids;

public class PostgresMailboxSetMethodTest implements MailboxSetMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_SUPPLIER.get().build();

    @Override
    public MailboxId randomMailboxId() {
        return PostgresMailboxId.of(Uuids.timeBased());
    }

    @Override
    public String errorInvalidMailboxIdMessage(String value) {
        return String.format("%s is not a mailboxId: Invalid UUID string: %s", value, value);
    }

    @Override
    @Test
    @Disabled("Distributed event bus is asynchronous, we cannot expect the newState to be returned immediately after Mailbox/set call")
    public void newStateShouldBeUpToDate(GuiceJamesServer server) {
    }

    @Override
    @Test
    @Disabled("Distributed event bus is asynchronous, we cannot expect the newState to be returned immediately after Mailbox/set call")
    public void oldStateShouldIncludeSetChanges(GuiceJamesServer server) {
    }
}
