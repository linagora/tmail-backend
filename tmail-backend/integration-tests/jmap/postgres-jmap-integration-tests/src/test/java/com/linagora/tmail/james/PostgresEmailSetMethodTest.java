package com.linagora.tmail.james;

import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_SUPPLIER;
import static com.linagora.tmail.james.TmailJmapBase.MESSAGE_ID_FACTORY;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.rfc8621.contract.EmailSetMethodContract;
import org.apache.james.mailbox.model.MessageId;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresEmailSetMethodTest implements EmailSetMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_SUPPLIER.get().build();

    @Override
    public MessageId randomMessageId() {
        return MESSAGE_ID_FACTORY.generate();
    }

    @Override
    public String invalidMessageIdMessage(String invalid) {
        return String.format("Invalid UUID string: %s", invalid);
    }

    @Override
    @Test
    @Disabled("Distributed event bus is asynchronous, we cannot expect the newState to be returned immediately after Email/set call")
    public void newStateShouldBeUpToDate(GuiceJamesServer server) {}

    @Override
    @Test
    @Disabled("Distributed event bus is asynchronous, we cannot expect the newState to be returned immediately after Email/set call")
    public void oldStateShouldIncludeSetChanges(GuiceJamesServer server) {}
}