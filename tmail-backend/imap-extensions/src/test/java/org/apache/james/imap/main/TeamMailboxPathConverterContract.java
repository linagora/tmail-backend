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
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.Test;

public interface TeamMailboxPathConverterContract {
    boolean RELATIVE = true;

    PathConverter teamMailboxPathConverter();

    Username teamMailboxUsername();

    MailboxSession mailboxSession();

    @Test
    default void buildFullPathShouldAcceptTeamMailboxName() {
        assertThat(teamMailboxPathConverter().buildFullPath("#TeamMailbox.sale"))
            .isEqualTo(new MailboxPath("#TeamMailbox", teamMailboxUsername(), "sale"));
    }

    @Test
    default void buildFullPathShouldShouldReturnFullTeamMailboxName() {
        assertThat(teamMailboxPathConverter().buildFullPath("#TeamMailbox.sale.INBOX"))
            .isEqualTo(new MailboxPath("#TeamMailbox", teamMailboxUsername(), "sale.INBOX"));
    }

    @Test
    default void buildFullPathWithTeamMailboxNamespaceShouldIgnoreCase() {
        assertThat(teamMailboxPathConverter().buildFullPath("#teammailbox.sale"))
            .isEqualTo(new MailboxPath("#TeamMailbox", teamMailboxUsername(), "sale"));
    }

    @Test
    default void mailboxNameShouldReturnNamespaceAndNameWhenRelative() {
        assertThat(teamMailboxPathConverter().mailboxName(RELATIVE, new MailboxPath("#TeamMailbox", teamMailboxUsername(), "sale"), mailboxSession()))
            .contains("#TeamMailbox.sale");
    }

    @Test
    default void mailboxNameShouldReturnNamespaceAndNameWhenNotRelative() {
        assertThat(teamMailboxPathConverter().mailboxName(!RELATIVE, new MailboxPath("#TeamMailbox", teamMailboxUsername(), "sale"), mailboxSession()))
            .contains("#TeamMailbox.sale");
    }
}
