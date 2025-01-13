package org.apache.james.imap.main;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.junit.jupiter.api.Nested;

import com.linagora.tmail.imap.TMailPathConverter;

public class TMailPathConverterTest extends PathConverterBasicContract implements TeamMailboxPathConverterContract {
    private final PathConverter pathConverter = new TMailPathConverter.Factory().forSession(mailboxSession);

    @Override
    public PathConverter pathConverter() {
        return pathConverter;
    }

    @Override
    public PathConverter teamMailboxPathConverter() {
        return pathConverter;
    }

    @Override
    public Username teamMailboxUsername() {
        return Username.of("team-mailbox");
    }

    @Override
    public MailboxSession mailboxSession() {
        return mailboxSession;
    }

    @Override
    public char folderDelimiter() {
        return MailboxConstants.MailboxFolderDelimiter.DOT.value;
    }

    @Nested
    class WithEmail extends PathConverterBasicContract.WithEmail implements TeamMailboxPathConverterContract {
        private final PathConverter pathConverter = new TMailPathConverter.Factory().forSession(mailboxWithEmailSession);

        @Override
        public PathConverter pathConverter() {
            return pathConverter;
        }

        @Override
        public PathConverter teamMailboxPathConverter() {
            return pathConverter;
        }

        @Override
        public Username teamMailboxUsername() {
            return Username.of("team-mailbox@apache.org");
        }

        @Override
        public MailboxSession mailboxSession() {
            return mailboxSession;
        }
    }
}
