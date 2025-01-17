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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.model.MailboxFolderDelimiterAwareTest;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.Test;

public abstract class TeamMailboxPathConverterContract extends MailboxFolderDelimiterAwareTest {
    public static final boolean RELATIVE = true;

    public final MailboxSession mailboxSession = MailboxSessionUtil.create(Username.of("username"), folderDelimiter());

    abstract PathConverter teamMailboxPathConverter();

    abstract Username teamMailboxUsername();


    @Test
    public void buildFullPathShouldAcceptTeamMailboxName() {
        assertThat(teamMailboxPathConverter().buildFullPath(adjustToActiveFolderDelimiter("#TeamMailbox.sale")))
            .isEqualTo(new MailboxPath("#TeamMailbox", teamMailboxUsername(), "sale"));
    }

    @Test
    public void buildFullPathShouldShouldReturnFullTeamMailboxName() {
        assertThat(teamMailboxPathConverter().buildFullPath(adjustToActiveFolderDelimiter("#TeamMailbox.sale.INBOX")))
            .isEqualTo(new MailboxPath("#TeamMailbox", teamMailboxUsername(),  adjustToActiveFolderDelimiter("sale.INBOX")));
    }

    @Test
    public void buildFullPathWithTeamMailboxNamespaceShouldIgnoreCase() {
        assertThat(teamMailboxPathConverter().buildFullPath(adjustToActiveFolderDelimiter("#teammailbox.sale")))
            .isEqualTo(new MailboxPath("#TeamMailbox", teamMailboxUsername(), "sale"));
    }

    @Test
    public void mailboxNameShouldReturnNamespaceAndNameWhenRelative() {
        assertThat(teamMailboxPathConverter().mailboxName(RELATIVE, new MailboxPath("#TeamMailbox", teamMailboxUsername(), "sale"), mailboxSession))
            .contains(adjustToActiveFolderDelimiter("#TeamMailbox.sale"));
    }

    @Test
    public void mailboxNameShouldReturnNamespaceAndNameWhenNotRelative() {
        assertThat(teamMailboxPathConverter().mailboxName(!RELATIVE, new MailboxPath("#TeamMailbox", teamMailboxUsername(), "sale"), mailboxSession))
            .contains(adjustToActiveFolderDelimiter("#TeamMailbox.sale"));
    }
}
