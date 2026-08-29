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
 * Decides what a completed SASL exchange means for the proxy.
 *
 * <p>The proxy holds no user database: a valid Kerberos ticket <strong>is</strong> the authentication,
 * exactly like James, which does not look the principal up either once GSSAPI succeeded. The identity
 * is taken as-is - the realm becomes the domain, no rewriting - so that the user the proxy resolves a
 * backend for, and the one it asks the backend to authorize it as, are the same string everywhere.
 *
 * <p>Delegation (a SASL authorization identity distinct from the authenticated principal) is refused:
 * the proxy has no way to tell which delegations a backend would allow.
 */
public class ProxySaslAuthenticator implements SaslAuthenticator {
    @Override
    public SaslAuthenticationResult authenticatePassword(Username authenticationId, Optional<Username> authorizationId,
                                                         String password) {
        // Unreachable: the proxy only ever exposes GSSAPI, which authenticates inside the SASL exchange.
        return new SaslAuthenticationResult.Failure(SaslFailure.authenticationFailed(Optional.of(authenticationId),
            authorizationId, "The migration proxy does not support password based SASL mechanisms."));
    }

    @Override
    public SaslAuthenticationResult authorize(SaslIdentity identity) {
        if (!identity.authenticationId().equals(identity.authorizationId())) {
            return new SaslAuthenticationResult.Failure(SaslFailure.delegationForbidden(identity.authenticationId(),
                identity.authorizationId(), "The migration proxy does not support SASL delegation."));
        }
        return new SaslAuthenticationResult.Success(identity);
    }
}
