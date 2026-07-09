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

package com.linagora.tmail.james.jmap.oidc;

import java.time.Clock;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.mailbox.SessionProvider;
import org.apache.james.oidc.Aud;
import org.apache.james.oidc.OidcTokenCache;

/**
 * TMail wrapper around James OIDC authentication strategy.
 * Kept under the historical TMail FQCN to avoid breaking existing deployments.
 */
public class OidcAuthenticationStrategy extends org.apache.james.jmap.http.OidcAuthenticationStrategy {
    public static final String TMAIL_AUTHENTICATION_CHALLENGE_REALM = "twake_mail";

    @Inject
    public OidcAuthenticationStrategy(SessionProvider sessionProvider, OidcTokenCache oidcTokenCache, Clock clock, List<Aud> auds) {
        super(sessionProvider, oidcTokenCache, clock, auds, TMAIL_AUTHENTICATION_CHALLENGE_REALM);
    }
}
