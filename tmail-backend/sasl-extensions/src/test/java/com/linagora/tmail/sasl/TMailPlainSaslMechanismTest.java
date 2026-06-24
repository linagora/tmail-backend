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

package com.linagora.tmail.sasl;

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
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("\0user+delegated@example.com\0" + PASSWORD)));

        SaslStep step = testee.start(request, authenticating()).firstStep();

        assertThat(step).isEqualTo(new SaslStep.Success(new SaslIdentity(AUTHENTICATION_ID, AUTHORIZATION_ID), Optional.empty()));
    }

    @Test
    void shouldReturnDelegatedIdentityWhenTwoPartUserTokenContainsPlus() {
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("user+delegated@example.com\0" + PASSWORD)));

        SaslStep step = testee.start(request, authenticating()).firstStep();

        assertThat(step).isEqualTo(new SaslStep.Success(new SaslIdentity(AUTHENTICATION_ID, AUTHORIZATION_ID), Optional.empty()));
    }

    @Test
    void shouldFailInvalidCredentialsWhenTwoPartDelegationSyntaxIsInvalid() {
        Username username = Username.of("user+@example.com");
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes(username.asString() + "\0" + PASSWORD)));

        SaslStep step = testee.start(request, authenticating()).firstStep();

        assertThat(step).isEqualTo(new SaslStep.Failure(SaslFailure.invalidCredentials(username, Optional.empty(), "Invalid credentials")));
    }

    @Test
    void shouldFailInvalidCredentialsWhenPlainDelegationSyntaxIsInvalid() {
        Username username = Username.of("user+@example.com");
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("\0" + username.asString() + "\0" + PASSWORD)));

        SaslStep step = testee.start(request, authenticating()).firstStep();

        assertThat(step).isEqualTo(new SaslStep.Failure(SaslFailure.invalidCredentials(username, Optional.empty(), "Invalid credentials")));
    }

    @Test
    void shouldFailMalformedWhenPlainTokenCountIsInvalid() {
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("only-one-token")));

        SaslStep step = testee.start(request, authenticating()).firstStep();

        assertThat(step).isEqualTo(new SaslStep.Failure(SaslFailure.malformed("Malformed authentication command.")));
    }

    @Test
    void shouldReturnNonDelegatedIdentityWhenUserTokenDoesNotContainPlus() {
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("\0" + AUTHENTICATION_ID.asString() + "\0" + PASSWORD)));

        SaslStep step = testee.start(request, authenticating()).firstStep();

        assertThat(step).isEqualTo(new SaslStep.Success(new SaslIdentity(AUTHENTICATION_ID, AUTHENTICATION_ID), Optional.empty()));
    }

    @Test
    void shouldPreserveWhitespaceOnlyPassword() {
        AtomicReference<String> capturedPassword = new AtomicReference<>();
        String whitespacePassword = "   ";
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("\0" + AUTHENTICATION_ID.asString() + "\0" + whitespacePassword)));

        testee.start(request, authenticating(capturedPassword)).firstStep();

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
