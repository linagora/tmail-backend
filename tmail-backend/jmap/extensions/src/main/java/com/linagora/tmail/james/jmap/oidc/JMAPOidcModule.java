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

import java.net.URL;

import org.apache.james.jwt.introspection.IntrospectionEndpoint;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;

public class JMAPOidcModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(TokenInfoResolver.class).to(OidcEndpointsInfoResolver.class);
    }

    @Provides
    @Named("userInfo")
    URL provideUserInfoEndpoint(JMAPOidcConfiguration configuration) {
        return configuration.getOidcUserInfoUrl();
    }

    @Provides
    IntrospectionEndpoint provideIntrospectionEndpoint(JMAPOidcConfiguration configuration) {
        return configuration.getIntrospectionEndpoint();
    }

    @Provides
    Aud provideAudience(JMAPOidcConfiguration configuration) {
        return configuration.getAud();
    }
}
