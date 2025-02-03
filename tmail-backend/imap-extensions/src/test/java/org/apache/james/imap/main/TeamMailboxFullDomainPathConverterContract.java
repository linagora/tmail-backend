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

import com.linagora.tmail.team.TeamMailbox;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxFolderDelimiterAwareTest;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class TeamMailboxFullDomainPathConverterContract extends MailboxFolderDelimiterAwareTest {
    public final MailboxSession mailboxSession = MailboxSessionUtil.create(Username.of("username"), folderDelimiter());

    abstract PathConverter pathConverter();

    public static String adjustToActiveFolderDelimiter(String valueWithSlashes) {
        // Using the super() method here is not possible, because we want to have . in our domains and this method
        // would not be able to distinguish between the delimiter dot and the domain dot.
        // For this reason, we also need to add escaping for contained dots if (and only if) it is used as the
        // active FOLDER_DELIMITER.
        if (MailboxConstants.FOLDER_DELIMITER == '.') {
            valueWithSlashes = valueWithSlashes.replace(".", "__");
        }
        return valueWithSlashes.replace('/', MailboxConstants.FOLDER_DELIMITER);
    }

    @Test
    public void buildFullPathShouldHandleTeamMailbox() {
        assertThat(pathConverter()
                .buildFullPath(adjustToActiveFolderDelimiter("#TeamMailbox/sale@james.local")))
                .isEqualTo(new MailboxPath(
                        "#TeamMailbox",
                        Username.fromLocalPartWithDomain(TeamMailbox.TEAM_MAILBOX_LOCAL_PART(), "james.local"),
                        "sale"));
    }

    @Test
    public void buildFullPathShouldIgnoreCase() {
        assertThat(pathConverter()
                .buildFullPath(adjustToActiveFolderDelimiter("#teammailbox/sale@james.local")))
                .isEqualTo(new MailboxPath(
                        "#TeamMailbox",
                        Username.fromLocalPartWithDomain(TeamMailbox.TEAM_MAILBOX_LOCAL_PART(), "james.local"),
                        "sale"));
    }

    @Test
    public void buildFullPathShouldCorrectlyTransformTeamMailboxWithFolder() {
        assertThat(pathConverter()
                .buildFullPath(adjustToActiveFolderDelimiter("#TeamMailbox/sale@james.local/INBOX")))
                .isEqualTo(new MailboxPath(
                        "#TeamMailbox",
                        Username.fromLocalPartWithDomain(TeamMailbox.TEAM_MAILBOX_LOCAL_PART(), "james.local"),
                        adjustToActiveFolderDelimiter("sale/INBOX")
                ));
    }

    @Test
    public void buildFullPathShouldCorrectlyTransformTeamMailboxWithSubFolder() {
        assertThat(pathConverter()
                .buildFullPath(adjustToActiveFolderDelimiter("#TeamMailbox/sale@james.local/INBOX/subfolder")))
                .isEqualTo(new MailboxPath(
                        "#TeamMailbox",
                        Username.fromLocalPartWithDomain(TeamMailbox.TEAM_MAILBOX_LOCAL_PART(), "james.local"),
                        adjustToActiveFolderDelimiter("sale/INBOX/subfolder")
                ));
    }

    @Test
    public void buildFullPathShouldUnescapeDomain() {
        assertThat(pathConverter()
                .buildFullPath(adjustToActiveFolderDelimiter("#TeamMailbox/sale@james_-long.local")))
                .isEqualTo(new MailboxPath(
                        "#TeamMailbox",
                        Username.fromLocalPartWithDomain(TeamMailbox.TEAM_MAILBOX_LOCAL_PART(), "james_long.local"),
                        "sale"));
    }

    @Test
    public void buildFullPathShouldRefuseMissingTeamMailboxName() {
        assertThatThrownBy(
                () -> pathConverter()
                        .buildFullPath(adjustToActiveFolderDelimiter("#TeamMailbox")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void buildFullPathShouldRefuseMailboxNameWithoutDomain() {
        assertThatThrownBy(
                () -> pathConverter()
                        .buildFullPath(adjustToActiveFolderDelimiter("#TeamMailbox/sale/INBOX")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void mailboxNameShouldHandleTeamMailbox(boolean isRelative) {
        assertThat(pathConverter().mailboxName(isRelative, new MailboxPath(
                "#TeamMailbox",
                Username.fromLocalPartWithDomain(TeamMailbox.TEAM_MAILBOX_LOCAL_PART(), "james.local"),
                "sale"
        ), mailboxSession))
                .contains(adjustToActiveFolderDelimiter("#TeamMailbox/sale@james.local"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void mailboxNameShouldCorrectlyTransformTeamMailboxWithFolder(boolean isRelative) {
        assertThat(pathConverter().mailboxName(isRelative, new MailboxPath(
                "#TeamMailbox",
                Username.fromLocalPartWithDomain(TeamMailbox.TEAM_MAILBOX_LOCAL_PART(), "james.local"),
                adjustToActiveFolderDelimiter("sale/INBOX")
        ), mailboxSession))
                .contains(adjustToActiveFolderDelimiter("#TeamMailbox/sale@james.local/INBOX"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void mailboxNameShouldCorrectlyTransformTeamMailboxWithSubFolder(boolean isRelative) {
        assertThat(pathConverter().mailboxName(isRelative, new MailboxPath(
                "#TeamMailbox",
                Username.fromLocalPartWithDomain(TeamMailbox.TEAM_MAILBOX_LOCAL_PART(), "james.local"),
                adjustToActiveFolderDelimiter("sale/INBOX/subfolder")
        ), mailboxSession))
                .contains(adjustToActiveFolderDelimiter("#TeamMailbox/sale@james.local/INBOX/subfolder"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void mailboxNameShouldRefusePathWithoutDomain(boolean isRelative) {
        assertThatThrownBy(
                () -> pathConverter().mailboxName(isRelative, new MailboxPath(
                        "#TeamMailbox",
                        Username.of(TeamMailbox.TEAM_MAILBOX_LOCAL_PART()),
                        "sale"), mailboxSession))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void mailboxNameShouldEscapeDomain(boolean isRelative) {
        assertThat(pathConverter().mailboxName(isRelative, new MailboxPath(
                "#TeamMailbox",
                Username.fromLocalPartWithDomain(TeamMailbox.TEAM_MAILBOX_LOCAL_PART(), "james_long.local"),
                "sale"
        ), mailboxSession))
                .contains(adjustToActiveFolderDelimiter("#TeamMailbox/sale@james_-long.local"));
    }
}
