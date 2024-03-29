package com.linagora.tmail.james;

import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_SUPPLIER;

import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.rfc8621.contract.MailboxQueryMethodContract;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresMailboxQueryMethodTest implements MailboxQueryMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_SUPPLIER.get().build();

}