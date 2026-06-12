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

package com.linagora.tmail.imap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.sasl.PlainSaslMechanism;
import org.apache.james.protocols.api.sasl.SaslCredentials;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.junit.jupiter.api.Test;

class TMailPlainSaslMechanismTest {
    private static final Username AUTHENTICATION_ID = Username.of("user@example.com");
    private static final Username AUTHORIZATION_ID = Username.of("delegated@example.com");
    private static final String PASSWORD = "secret";

    private final TMailPlainSaslMechanism testee = new TMailPlainSaslMechanism();

    @Test
    void shouldReturnDelegatedPasswordCredentialsWhenUserTokenContainsPlus() {
        // GIVEN a TMail PLAIN response using user+delegated syntax
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("\0user+delegated@example.com\0" + PASSWORD)));

        // WHEN the mechanism parses the response
        SaslStep step = testee.start(request).firstStep();

        // THEN it preserves the authentication and authorization identities for IMAP delegation
        assertThat(step).isEqualTo(new SaslStep.Credentials(new SaslCredentials.Password(
            AUTHENTICATION_ID, Optional.of(AUTHORIZATION_ID), PASSWORD)));
    }

    @Test
    void shouldReturnNonDelegatedPasswordCredentialsWhenUserTokenDoesNotContainPlus() {
        // GIVEN a regular TMail PLAIN response
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("\0" + AUTHENTICATION_ID.asString() + "\0" + PASSWORD)));

        // WHEN the mechanism parses the response
        SaslStep step = testee.start(request).firstStep();

        // THEN it keeps the regular non-delegated credentials shape
        assertThat(step).isEqualTo(new SaslStep.Credentials(new SaslCredentials.Password(
            AUTHENTICATION_ID, Optional.empty(), PASSWORD)));
    }

    @Test
    void shouldPreserveWhitespaceOnlyPassword() {
        // GIVEN a PLAIN response whose password is made of whitespace only
        String whitespacePassword = "   ";
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("\0" + AUTHENTICATION_ID.asString() + "\0" + whitespacePassword)));

        // WHEN the mechanism parses the response
        SaslStep step = testee.start(request).firstStep();

        // THEN it keeps the password unchanged instead of filtering it as blank
        assertThat(step).isEqualTo(new SaslStep.Credentials(new SaslCredentials.Password(
            AUTHENTICATION_ID, Optional.empty(), whitespacePassword)));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
