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

    @Test
    public void mailboxNameShouldRenderTeamMailboxOfTheSessionDomain() {
        MailboxSession domainSession = MailboxSessionUtil.create(Username.of("user@domain.tld"), folderDelimiter());
        MailboxPath sameDomainTeam = new MailboxPath("#TeamMailbox", Username.of("team-mailbox@domain.tld"), "sales");

        assertThat(teamMailboxPathConverter().mailboxName(!RELATIVE, sameDomainTeam, domainSession))
            .contains(adjustToActiveFolderDelimiter("#TeamMailbox.sales"));
    }

    @Test
    public void mailboxNameOfATeamMailboxOfAnotherDomainShouldBeQualifiedWithItsDomain() {
        MailboxSession domainSession = MailboxSessionUtil.create(Username.of("user@domain.tld"), folderDelimiter());
        MailboxPath otherDomainTeam = new MailboxPath("#TeamMailbox", Username.of("team-mailbox@other.tld"), "sales");

        assertThat(teamMailboxPathConverter().mailboxName(!RELATIVE, otherDomainTeam, domainSession))
            .contains(adjustToActiveFolderDelimiter("#TeamMailbox.sales@") + escapedOtherDomain());
    }

    @Test
    public void mailboxNameOfAFolderOfATeamMailboxOfAnotherDomainShouldBeQualifiedWithItsDomain() {
        MailboxSession domainSession = MailboxSessionUtil.create(Username.of("user@domain.tld"), folderDelimiter());
        MailboxPath otherDomainTeam = new MailboxPath("#TeamMailbox", Username.of("team-mailbox@other.tld"), adjustToActiveFolderDelimiter("sales.INBOX"));

        assertThat(teamMailboxPathConverter().mailboxName(!RELATIVE, otherDomainTeam, domainSession))
            .contains(adjustToActiveFolderDelimiter("#TeamMailbox.sales@") + escapedOtherDomain() + adjustToActiveFolderDelimiter(".INBOX"));
    }

    @Test
    public void mailboxNameOfATeamMailboxWithADomainShouldBeQualifiedForASessionWithoutDomain() {
        MailboxPath otherDomainTeam = new MailboxPath("#TeamMailbox", Username.of("team-mailbox@other.tld"), "sales");

        assertThat(teamMailboxPathConverter().mailboxName(!RELATIVE, otherDomainTeam, mailboxSession))
            .contains(adjustToActiveFolderDelimiter("#TeamMailbox.sales@") + escapedOtherDomain());
    }

    @Test
    public void buildFullPathShouldResolveADomainQualifiedTeamMailboxName() {
        assertThat(teamMailboxPathConverter().buildFullPath(adjustToActiveFolderDelimiter("#TeamMailbox.sales@") + escapedOtherDomain()))
            .isEqualTo(new MailboxPath("#TeamMailbox", Username.of("team-mailbox@other.tld"), "sales"));
    }

    @Test
    public void buildFullPathShouldResolveAFolderOfADomainQualifiedTeamMailboxName() {
        assertThat(teamMailboxPathConverter().buildFullPath(adjustToActiveFolderDelimiter("#TeamMailbox.sales@") + escapedOtherDomain() + adjustToActiveFolderDelimiter(".INBOX")))
            .isEqualTo(new MailboxPath("#TeamMailbox", Username.of("team-mailbox@other.tld"), adjustToActiveFolderDelimiter("sales.INBOX")));
    }

    @Test
    public void teamMailboxOfAnotherDomainShouldRoundTrip() {
        MailboxSession domainSession = MailboxSessionUtil.create(Username.of("user@domain.tld"), folderDelimiter());
        MailboxPath otherDomainTeam = new MailboxPath("#TeamMailbox", Username.of("team-mailbox@other_domain.tld"), adjustToActiveFolderDelimiter("sales.INBOX"));

        assertThat(teamMailboxPathConverter().mailboxName(!RELATIVE, otherDomainTeam, domainSession)
                .map(teamMailboxPathConverter()::buildFullPath))
            .contains(otherDomainTeam);
    }

    private String escapedOtherDomain() {
        return "other.tld".replace(String.valueOf(folderDelimiter()), "__");
    }
}
