package org.apache.james.imap.main;

import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MailboxConstants;
import org.junit.jupiter.api.Nested;

import com.linagora.tmail.imap.TMailPathConverter;

public class TMailPathConverterTest {
    @Nested
    public class BasicContract {
        @Nested
        public class DotDelimiter extends TestBase {
            @Override
            public char folderDelimiter() {
                return MailboxConstants.MailboxFolderDelimiter.DOT.value;
            }
        }

        @Nested
        public class SlashDelimiter extends TestBase {
            @Override
            public char folderDelimiter() {
                return MailboxConstants.MailboxFolderDelimiter.SLASH.value;
            }
        }

        @Nested
        public class PipeDelimiter extends TestBase {
            @Override
            public char folderDelimiter() {
                return MailboxConstants.MailboxFolderDelimiter.PIPE.value;
            }
        }

        @Nested
        public class CommaDelimiter extends TestBase {
            @Override
            public char folderDelimiter() {
                return MailboxConstants.MailboxFolderDelimiter.COMMA.value;
            }
        }

        @Nested
        public class ColonDelimiter extends TestBase {
            @Override
            public char folderDelimiter() {
                return MailboxConstants.MailboxFolderDelimiter.COLON.value;
            }
        }

        @Nested
        public class SemicolonDelimiter extends TestBase {
            @Override
            public char folderDelimiter() {
                return MailboxConstants.MailboxFolderDelimiter.SEMICOLON.value;
            }
        }

        public abstract static class TestBase extends PathConverterBasicContract {
            private final PathConverter pathConverter = new TMailPathConverter.Factory().forSession(mailboxSession);

            @Override
            public PathConverter pathConverter() {
                return pathConverter;
            }

            @Nested
            class WithEmail extends PathConverterBasicContract.WithEmail {
                private final PathConverter pathConverter = new TMailPathConverter.Factory().forSession(mailboxWithEmailSession);

                @Override
                public PathConverter pathConverter() {
                    return pathConverter;
                }
            }
        }
    }

    @Nested
    public class TeamMailboxContract {
        @Nested
        public class DotDelimiter extends TestBase {
            @Override
            public char folderDelimiter() {
                return MailboxConstants.MailboxFolderDelimiter.DOT.value;
            }
        }

        @Nested
        public class SlashDelimiter extends TestBase {
            @Override
            public char folderDelimiter() {
                return MailboxConstants.MailboxFolderDelimiter.SLASH.value;
            }
        }

        @Nested
        public class PipeDelimiter extends TestBase {
            @Override
            public char folderDelimiter() {
                return MailboxConstants.MailboxFolderDelimiter.PIPE.value;
            }
        }

        @Nested
        public class CommaDelimiter extends TestBase {
            @Override
            public char folderDelimiter() {
                return MailboxConstants.MailboxFolderDelimiter.COMMA.value;
            }
        }

        @Nested
        public class ColonDelimiter extends TestBase {
            @Override
            public char folderDelimiter() {
                return MailboxConstants.MailboxFolderDelimiter.COLON.value;
            }
        }

        @Nested
        public class SemicolonDelimiter extends TestBase {
            @Override
            public char folderDelimiter() {
                return MailboxConstants.MailboxFolderDelimiter.SEMICOLON.value;
            }
        }

        public abstract static class TestBase extends TeamMailboxPathConverterContract {
            private final PathConverter pathConverter = new TMailPathConverter.Factory().forSession(mailboxSession);

            @Override
            public PathConverter teamMailboxPathConverter() {
                return pathConverter;
            }

            @Override
            public Username teamMailboxUsername() {
                return Username.of("team-mailbox");
            }
        }
    }
}
