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

package imap.linagora.tmail.imap;

import org.apache.james.core.Username;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.main.PathConverterBasicContract;
import org.apache.james.mailbox.MailboxSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;

import com.linagora.tmail.imap.TMailPathConverter;

public class TMailPathConverterTest implements PathConverterBasicContract, TeamMailboxPathConverterContract {

    private PathConverter pathConverter;

    @BeforeEach
    void setup() {
        pathConverter = new TMailPathConverter.Factory().forSession(mailboxSession);
    }

    @Override
    public PathConverter pathConverter() {
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

    @Nested
    class WithEmailTest implements PathConverterBasicContract.WithEmail, TeamMailboxPathConverterContract {

        private PathConverter pathConverter;

        @BeforeEach
        void setup() {
            pathConverter = new TMailPathConverter.Factory().forSession(mailboxSession);
        }

        @Override
        public PathConverter pathConverter() {
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
