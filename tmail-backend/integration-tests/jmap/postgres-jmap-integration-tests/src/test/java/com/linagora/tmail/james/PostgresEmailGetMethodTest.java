package com.linagora.tmail.james;

import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION;
import static com.linagora.tmail.james.TmailJmapBase.MESSAGE_ID_FACTORY;

import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.rfc8621.contract.EmailGetMethodContract;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.mailbox.model.MessageId;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresEmailGetMethodTest implements EmailGetMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_FUNCTION.apply(new DelegationProbeModule())
        .build();

    @Override
    public MessageId randomMessageId() {
        return MESSAGE_ID_FACTORY.generate();
    }
}
