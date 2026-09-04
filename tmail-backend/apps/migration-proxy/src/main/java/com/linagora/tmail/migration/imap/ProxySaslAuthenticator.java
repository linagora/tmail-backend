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

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.sasl.SaslAuthenticationResult;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslIdentity;

/**
 * Decides what a completed SASL exchange means for the proxy. One instance per {@code AUTHENTICATE}
 * command: it carries the credentials the exchange yielded.
 *
 * <p>The proxy holds no user database, so the two mechanisms it exposes end differently:
 * <ul>
 *   <li>{@code PLAIN} hands it a password, which it does not - cannot - verify. It captures it and the
 *   backend is the authority: a wrong password fails the relay, which the client reads as a {@code NO}.</li>
 *   <li>{@code GSSAPI} authenticated the user inside the exchange. Like James, which does not look the
 *   principal up either, the proxy takes the identity as-is - the realm becomes the domain, no
 *   rewriting - so that the user it resolves a backend for, and the one it opens the backend session
 *   for, are the same string everywhere.</li>
 * </ul>
 *
 * <p>Delegation (an authorization identity distinct from the authenticated one) is refused in both
 * cases: the proxy has no way to tell which delegations a backend would allow.
 */
public class ProxySaslAuthenticator implements SaslAuthenticator {
    private Optional<String> capturedPassword = Optional.empty();

    @Override
    public SaslAuthenticationResult authenticatePassword(Username authenticationId, Optional<Username> authorizationId,
                                                         String password) {
        if (isDelegation(authenticationId, authorizationId)) {
            return new SaslAuthenticationResult.Failure(SaslFailure.delegationForbidden(authenticationId,
                authorizationId.get(), "The migration proxy does not support SASL delegation."));
        }
        capturedPassword = Optional.of(password);
        return new SaslAuthenticationResult.Success(new SaslIdentity(authenticationId, authenticationId));
    }

    @Override
    public SaslAuthenticationResult authorize(SaslIdentity identity) {
        if (!identity.authenticationId().equals(identity.authorizationId())) {
            return new SaslAuthenticationResult.Failure(SaslFailure.delegationForbidden(identity.authenticationId(),
                identity.authorizationId(), "The migration proxy does not support SASL delegation."));
        }
        return new SaslAuthenticationResult.Success(identity);
    }

    /**
     * The password the exchange captured, if the mechanism carried one: it is then replayed against the
     * backend. Empty for GSSAPI, where the proxy opens the backend session by delegation instead.
     */
    public Optional<String> capturedPassword() {
        return capturedPassword;
    }

    private static boolean isDelegation(Username authenticationId, Optional<Username> authorizationId) {
        return authorizationId
            .filter(identity -> !identity.equals(authenticationId))
            .isPresent();
    }
}
