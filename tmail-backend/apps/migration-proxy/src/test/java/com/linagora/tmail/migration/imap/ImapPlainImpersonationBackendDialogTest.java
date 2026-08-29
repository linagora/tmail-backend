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

package com.linagora.tmail.migration.imap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.linagora.tmail.migration.core.AdminCredentials;
import com.linagora.tmail.migration.core.BackendDialog;
import com.linagora.tmail.migration.core.BackendDialog.Decision;

class ImapPlainImpersonationBackendDialogTest {
    private static final AdminCredentials ADMIN = new AdminCredentials("admin@domain.tld", "admin-password");
    private static final String AUTHENTICATE_PLAIN = ImapBackendDialog.PROXY_TAG + " AUTHENTICATE PLAIN";

    @Test
    void shouldAuthenticateAsAdminOnBehalfOfTheUser() {
        BackendDialog dialog = new ImapPlainImpersonationBackendDialog("bob@domain.tld", ADMIN);

        BackendDialog.Action onGreeting = dialog.onLine("* OK [CAPABILITY IMAP4rev1 AUTH=PLAIN] backend ready");
        assertThat(onGreeting.decision()).isEqualTo(Decision.SEND);
        assertThat(onGreeting.command()).isEqualTo(AUTHENTICATE_PLAIN);

        BackendDialog.Action onChallenge = dialog.onLine("+ ");
        assertThat(onChallenge.decision()).isEqualTo(Decision.SEND);
        // authzid \0 authcid \0 password: the backend authenticates the admin and authorizes it as the user.
        assertThat(decode(onChallenge.command()))
            .isEqualTo("bob@domain.tld" + '\0' + "admin@domain.tld" + '\0' + "admin-password");

        assertThat(dialog.onLine(ImapBackendDialog.PROXY_TAG + " OK AUTHENTICATE completed").decision())
            .isEqualTo(Decision.SUCCESS);
    }

    @Test
    void shouldWaitForUntaggedLines() {
        BackendDialog dialog = new ImapPlainImpersonationBackendDialog("bob@domain.tld", ADMIN);
        dialog.onLine("* OK backend ready");
        dialog.onLine("+ ");

        assertThat(dialog.onLine("* CAPABILITY IMAP4rev1").decision()).isEqualTo(Decision.WAIT);
    }

    @Test
    void shouldFailWhenBackendRejectsTheDelegation() {
        BackendDialog dialog = new ImapPlainImpersonationBackendDialog("bob@domain.tld", ADMIN);
        dialog.onLine("* OK backend ready");
        dialog.onLine("+ ");

        assertThat(dialog.onLine(ImapBackendDialog.PROXY_TAG + " NO AUTHENTICATE failed").decision())
            .isEqualTo(Decision.FAILURE);
    }

    @Test
    void shouldFailWhenBackendRejectsThePlainMechanism() {
        BackendDialog dialog = new ImapPlainImpersonationBackendDialog("bob@domain.tld", ADMIN);
        dialog.onLine("* OK backend ready");

        assertThat(dialog.onLine(ImapBackendDialog.PROXY_TAG + " NO PLAIN is not supported").decision())
            .isEqualTo(Decision.FAILURE);
    }

    private static String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }
}
