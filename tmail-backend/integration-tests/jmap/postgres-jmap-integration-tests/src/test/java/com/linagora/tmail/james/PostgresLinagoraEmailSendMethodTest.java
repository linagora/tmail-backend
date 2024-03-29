package com.linagora.tmail.james;

import org.apache.james.JamesServerExtension;
import org.apache.james.mailbox.model.MessageId;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.common.LinagoraEmailSendMethodContract;

public class PostgresLinagoraEmailSendMethodTest implements LinagoraEmailSendMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = TmailJmapBase.JAMES_SERVER_EXTENSION_SUPPLIER.get().build();

    @Override
    public MessageId randomMessageId() {
        return TmailJmapBase.MESSAGE_ID_FACTORY.generate();
    }
}