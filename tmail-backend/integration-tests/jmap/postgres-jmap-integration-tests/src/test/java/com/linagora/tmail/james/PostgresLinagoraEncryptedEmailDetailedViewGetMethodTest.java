package com.linagora.tmail.james;

import static com.linagora.tmail.james.PostgresLinagoraEncryptedEmailFastViewGetMethodTest.ENCRYPTED_JAMES_SERVER;

import org.apache.james.JamesServerExtension;
import org.apache.james.mailbox.model.MessageId;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.common.LinagoraEncryptedEmailDetailedViewGetMethodContract;

public class PostgresLinagoraEncryptedEmailDetailedViewGetMethodTest implements LinagoraEncryptedEmailDetailedViewGetMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = ENCRYPTED_JAMES_SERVER.get();

    @Override
    public MessageId randomMessageId() {
        return TmailJmapBase.MESSAGE_ID_FACTORY.generate();
    }
}
