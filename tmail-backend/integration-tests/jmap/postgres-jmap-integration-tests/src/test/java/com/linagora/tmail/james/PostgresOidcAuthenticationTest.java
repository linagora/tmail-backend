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

package com.linagora.tmail.james;

import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION;

import java.net.URL;
import java.util.Optional;

import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.core.JmapRfc8621Configuration;
import org.apache.james.jwt.introspection.IntrospectionEndpoint;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import com.linagora.tmail.common.OidcAuthenticationContract;

public class PostgresOidcAuthenticationTest extends OidcAuthenticationContract {
    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_FUNCTION
        .apply(Modules.combine(
            binder -> binder.bind(JmapRfc8621Configuration.class)
                .toInstance(JmapRfc8621Configuration.LOCALHOST_CONFIGURATION()
                    .withAuthenticationStrategies(OIDC_AUTHENTICATION_STRATEGY)),
            binder -> binder.bind(URL.class).annotatedWith(Names.named("userInfo"))
                .toProvider(OidcAuthenticationContract::getUserInfoTokenEndpoint),
            binder -> binder.bind(IntrospectionEndpoint.class)
                .toProvider(() -> new IntrospectionEndpoint(getIntrospectTokenEndpoint(), Optional.empty()))))
        .build();
}
