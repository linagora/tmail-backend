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
import java.util.concurrent.atomic.AtomicReference;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.sasl.SaslAuthenticationResult;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.sasl.plain.PlainSaslMechanism;
import org.junit.jupiter.api.Test;

class TMailPlainSaslMechanismTest {
    private static final Username AUTHENTICATION_ID = Username.of("user@example.com");
    private static final Username AUTHORIZATION_ID = Username.of("delegated@example.com");
    private static final String PASSWORD = "secret";

    private final TMailPlainSaslMechanism testee = new TMailPlainSaslMechanism();

    @Test
    void shouldReturnDelegatedIdentityWhenUserTokenContainsPlus() {
        // GIVEN a TMail PLAIN response using user+delegated syntax
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("\0user+delegated@example.com\0" + PASSWORD)));

        // WHEN the mechanism parses and authenticates the response
        SaslStep step = testee.start(request, authenticating()).firstStep();

        // THEN it preserves the authentication and authorization identities for IMAP delegation
        assertThat(step).isEqualTo(new SaslStep.Success(new SaslIdentity(AUTHENTICATION_ID, AUTHORIZATION_ID), Optional.empty()));
    }

    @Test
    void shouldReturnDelegatedIdentityForLoginUserTokenContainingPlus() {
        // GIVEN a TMail LOGIN username using user+delegated syntax

        // WHEN the LOGIN path authenticates through the TMail PLAIN mechanism override
        SaslStep step = testee.authenticate(Username.of("user+delegated@example.com"), PASSWORD, authenticating());

        // THEN the same delegation identity is produced without duplicating auth logic in a TMail login processor
        assertThat(step).isEqualTo(new SaslStep.Success(new SaslIdentity(AUTHENTICATION_ID, AUTHORIZATION_ID), Optional.empty()));
    }

    @Test
    void shouldFailMalformedLoginDelegationSyntax() {
        // GIVEN a TMail LOGIN username with malformed delegation syntax

        // WHEN the LOGIN path authenticates through the TMail PLAIN mechanism override
        SaslStep step = testee.authenticate(Username.of("user+@example.com"), PASSWORD, authenticating());

        // THEN the malformed syntax is reported as a SASL failure instead of throwing
        assertThat(step).isEqualTo(new SaslStep.Failure(SaslFailure.malformed("Malformed authentication command.")));
    }

    @Test
    void shouldReturnNonDelegatedIdentityWhenUserTokenDoesNotContainPlus() {
        // GIVEN a regular TMail PLAIN response
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("\0" + AUTHENTICATION_ID.asString() + "\0" + PASSWORD)));

        // WHEN the mechanism parses and authenticates the response
        SaslStep step = testee.start(request, authenticating()).firstStep();

        // THEN it keeps the regular non-delegated identity shape
        assertThat(step).isEqualTo(new SaslStep.Success(new SaslIdentity(AUTHENTICATION_ID, AUTHENTICATION_ID), Optional.empty()));
    }

    @Test
    void shouldPreserveWhitespaceOnlyPassword() {
        // GIVEN a PLAIN response whose password is made of whitespace only
        AtomicReference<String> capturedPassword = new AtomicReference<>();
        String whitespacePassword = "   ";
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("\0" + AUTHENTICATION_ID.asString() + "\0" + whitespacePassword)));

        // WHEN the mechanism parses and authenticates the response
        testee.start(request, authenticating(capturedPassword)).firstStep();

        // THEN it keeps the password unchanged instead of filtering it as blank
        assertThat(capturedPassword).hasValue(whitespacePassword);
    }

    private static SaslAuthenticator authenticating() {
        return authenticating(new AtomicReference<>());
    }

    private static SaslAuthenticator authenticating(AtomicReference<String> capturedPassword) {
        return new SaslAuthenticator() {
            @Override
            public SaslAuthenticationResult authenticatePassword(Username authenticationId, Optional<Username> authorizationId, String password) {
                capturedPassword.set(password);
                return new SaslAuthenticationResult.Success(new SaslIdentity(authenticationId, authorizationId.orElse(authenticationId)));
            }

            @Override
            public SaslAuthenticationResult authorize(SaslIdentity identity) {
                return new SaslAuthenticationResult.Failure(SaslFailure.delegationForbidden(
                    identity.authenticationId(), identity.authorizationId(), "unused"));
            }
        };
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
