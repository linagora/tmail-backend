/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package org.apache.james.imap.main;

import com.linagora.tmail.imap.TMailPathConverterFactory;
import org.apache.james.mailbox.model.MailboxConstants;
import org.junit.jupiter.api.Nested;

public class TMailFullDomainPathConverterTest {
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
            private final PathConverter pathConverter = new TMailPathConverterFactory()
                    .fullDomainPathConverterForSession(mailboxSession);

            @Override
            public PathConverter pathConverter() {
                return pathConverter;
            }

            @Nested
            class WithEmail extends PathConverterBasicContract.WithEmail {
                private final PathConverter pathConverter = new TMailPathConverterFactory()
                        .fullDomainPathConverterForSession(mailboxWithEmailSession);

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

        public abstract static class TestBase extends TeamMailboxFullDomainPathConverterContract {
            private final PathConverter pathConverter = new TMailPathConverterFactory()
                    .fullDomainPathConverterForSession(mailboxSession);

            @Override
            public PathConverter pathConverter() {
                return pathConverter;
            }
        }
    }
}
