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


package com.linagora.tmail.migration.submission;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.linagora.tmail.migration.core.BackendDialog;
import com.linagora.tmail.migration.core.BackendDialog.Decision;
import com.linagora.tmail.migration.submission.SmtpBackendDialog.Outcome;

class SmtpBackendDialogTest {
    private static final String AUTH_COMMAND = "AUTH PLAIN " + Base64.getEncoder()
        .encodeToString("\0bob@domain.tld\0secret".getBytes(StandardCharsets.UTF_8));

    private static SmtpBackendDialog dialog() {
        return new SmtpBackendDialog("proxy.domain.tld", "bob@domain.tld", "secret");
    }

    private static SmtpBackendDialog awaitingAuthReply() {
        SmtpBackendDialog dialog = dialog();
        dialog.onLine("220 backend ESMTP");
        dialog.onLine("250 backend");
        return dialog;
    }

    @Test
    void shouldGreetThenAuthenticateThenSucceed() {
        SmtpBackendDialog dialog = dialog();

        BackendDialog.Action onGreeting = dialog.onLine("220 backend ESMTP ready");
        assertThat(onGreeting.decision()).isEqualTo(Decision.SEND);
        assertThat(onGreeting.command()).isEqualTo("EHLO proxy.domain.tld");

        assertThat(dialog.onLine("250-backend").decision()).isEqualTo(Decision.WAIT);
        assertThat(dialog.onLine("250-PIPELINING").decision()).isEqualTo(Decision.WAIT);
        BackendDialog.Action onEhlo = dialog.onLine("250 AUTH PLAIN LOGIN");
        assertThat(onEhlo.decision()).isEqualTo(Decision.SEND);
        assertThat(onEhlo.command()).isEqualTo(AUTH_COMMAND);

        assertThat(dialog.onLine("235 2.7.0 Authentication successful").decision()).isEqualTo(Decision.SUCCESS);
        assertThat(dialog.outcome()).isEqualTo(Outcome.AUTHENTICATED);
    }

    @Test
    void shouldWaitForTheLastLineOfAMultiLineGreeting() {
        SmtpBackendDialog dialog = dialog();

        assertThat(dialog.onLine("220-backend ESMTP").decision()).isEqualTo(Decision.WAIT);
        assertThat(dialog.onLine("220-second line").decision()).isEqualTo(Decision.WAIT);
        assertThat(dialog.onLine("220 ready").command()).isEqualTo("EHLO proxy.domain.tld");
    }

    @Test
    void shouldAcceptABareReplyCode() {
        SmtpBackendDialog dialog = dialog();

        assertThat(dialog.onLine("220").command()).isEqualTo("EHLO proxy.domain.tld");
        assertThat(dialog.onLine("250").command()).isEqualTo(AUTH_COMMAND);
        assertThat(dialog.onLine("235").decision()).isEqualTo(Decision.SUCCESS);
    }

    @Test
    void outcomeShouldBePendingUntilAConclusion() {
        SmtpBackendDialog dialog = dialog();
        dialog.onLine("220 backend ESMTP");

        assertThat(dialog.outcome()).isEqualTo(Outcome.PENDING);
    }

    @Test
    void aRejectedPasswordShouldBeACredentialsRejection() {
        SmtpBackendDialog dialog = awaitingAuthReply();

        assertThat(dialog.onLine("535 5.7.8 Authentication credentials invalid").decision()).isEqualTo(Decision.FAILURE);
        assertThat(dialog.outcome()).isEqualTo(Outcome.CREDENTIALS_REJECTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"454 4.7.0 Temporary authentication failure", "504 5.5.4 Unrecognized authentication type",
        "534 5.7.9 Authentication mechanism is too weak", "538 5.7.11 Encryption required", "334 ", "500 unknown"})
    void anyOtherAuthReplyShouldBeABackendFailure(String reply) {
        SmtpBackendDialog dialog = awaitingAuthReply();

        assertThat(dialog.onLine(reply).decision()).isEqualTo(Decision.FAILURE);
        assertThat(dialog.outcome()).isEqualTo(Outcome.BACKEND_FAILURE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"421 4.3.2 Service not available", "554 5.3.2 No service here", "250 not a greeting"})
    void aGreetingRefusalShouldBeABackendFailure(String greeting) {
        SmtpBackendDialog dialog = dialog();

        assertThat(dialog.onLine(greeting).decision()).isEqualTo(Decision.FAILURE);
        assertThat(dialog.outcome()).isEqualTo(Outcome.BACKEND_FAILURE);
    }

    @Test
    void anEhloRefusalShouldBeABackendFailure() {
        SmtpBackendDialog dialog = dialog();
        dialog.onLine("220 backend ESMTP");

        assertThat(dialog.onLine("502 5.5.1 EHLO not implemented").decision()).isEqualTo(Decision.FAILURE);
        assertThat(dialog.outcome()).isEqualTo(Outcome.BACKEND_FAILURE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"* OK IMAP4rev1 ready", "", "22", "2x0 bad", "220x", "HTTP/1.1 400 Bad Request"})
    void aLineThatIsNotAnSmtpReplyShouldBeABackendFailure(String line) {
        SmtpBackendDialog dialog = dialog();

        assertThat(dialog.onLine(line).decision()).isEqualTo(Decision.FAILURE);
        assertThat(dialog.outcome()).isEqualTo(Outcome.BACKEND_FAILURE);
    }

    @Test
    void anythingAfterAConclusionShouldBeAFailure() {
        SmtpBackendDialog dialog = awaitingAuthReply();
        dialog.onLine("535 5.7.8 Authentication credentials invalid");

        assertThat(dialog.onLine("235 2.7.0 Authentication successful").decision()).isEqualTo(Decision.FAILURE);
        assertThat(dialog.outcome()).isEqualTo(Outcome.BACKEND_FAILURE);
    }

    @Test
    void credentialsShouldBeEncodedAsUtf8() {
        SmtpBackendDialog dialog = new SmtpBackendDialog("proxy", "rené@domain.tld", "pâssword");
        dialog.onLine("220 backend");

        String initialResponse = dialog.onLine("250 backend").command().substring("AUTH PLAIN ".length());
        assertThat(new String(Base64.getDecoder().decode(initialResponse), StandardCharsets.UTF_8))
            .isEqualTo("\0rené@domain.tld\0pâssword");
    }
}
