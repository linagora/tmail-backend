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

import org.junit.jupiter.api.Test;

import com.linagora.tmail.migration.core.BackendDialog;
import com.linagora.tmail.migration.core.BackendDialog.Decision;

class ImapBackendDialogTest {
    @Test
    void shouldLoginAfterGreetingThenSucceed() {
        BackendDialog dialog = new ImapBackendDialog("bob@domain.tld", "secret");

        BackendDialog.Action onGreeting = dialog.onLine("* OK [CAPABILITY IMAP4rev1] backend ready");
        assertThat(onGreeting.decision()).isEqualTo(Decision.SEND);
        assertThat(onGreeting.command()).isEqualTo(ImapBackendDialog.PROXY_TAG + " LOGIN \"bob@domain.tld\" \"secret\"");

        assertThat(dialog.onLine("* CAPABILITY IMAP4rev1").decision()).isEqualTo(Decision.WAIT);
        assertThat(dialog.onLine(ImapBackendDialog.PROXY_TAG + " OK LOGIN completed").decision()).isEqualTo(Decision.SUCCESS);
    }

    @Test
    void shouldFailWhenLoginRejected() {
        BackendDialog dialog = new ImapBackendDialog("bob@domain.tld", "wrong");
        dialog.onLine("* OK backend ready");

        assertThat(dialog.onLine(ImapBackendDialog.PROXY_TAG + " NO LOGIN failed").decision()).isEqualTo(Decision.FAILURE);
    }

    @Test
    void shouldQuoteSpecialCharactersInCredentials() {
        BackendDialog dialog = new ImapBackendDialog("a\"b", "c\\d");

        BackendDialog.Action onGreeting = dialog.onLine("* OK ready");
        assertThat(onGreeting.command()).isEqualTo(ImapBackendDialog.PROXY_TAG + " LOGIN \"a\\\"b\" \"c\\\\d\"");
    }
}
