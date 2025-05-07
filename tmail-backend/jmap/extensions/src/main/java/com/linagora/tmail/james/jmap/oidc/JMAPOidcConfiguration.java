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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.jwt.introspection.IntrospectionEndpoint;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.james.jmap.model.Aud;

public class JMAPOidcConfiguration {
    private static final String USERINFO_URL_PROPERTY = "oidc.userInfo.url";
    private static final String INTROSPECT_URL_PROPERTY = "oidc.introspect.url";
    private static final String INTROSPECT_CREDENTIALS_PROPERTY = "oidc.introspect.credentials";
    private static final String AUDIENCE_PROPERTY = "oidc.audience";
    private static final String CLAIM_PROPERTY = "oidc.claim";

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<URL> oidcUserInfoUrl = Optional.empty();
        private Optional<IntrospectionEndpoint> oidcIntrospectionEndpoint = Optional.empty();
        private Optional<String> oidcIntrospectionClaim = Optional.empty();
        private Optional<Aud> oidcAudience = Optional.empty();

        public Builder oidcUserInfoUrl(Optional<URL> url) {
            this.oidcUserInfoUrl = url;
            return this;
        }

        public Builder oidcIntrospectionEndpoint(Optional<IntrospectionEndpoint> endpoint) {
            this.oidcIntrospectionEndpoint = endpoint;
            return this;
        }

        public Builder oidcClaim(Optional<String> claim) {
            this.oidcIntrospectionClaim = claim;
            return this;
        }

        public Builder oidcAudience(Optional<Aud> aud) {
            this.oidcAudience = aud;
            return this;
        }

        public JMAPOidcConfiguration build() {
            try {
                return new JMAPOidcConfiguration(
                    oidcUserInfoUrl.orElse(new URL("http://keycloak:8080/auth/realms/oidc/protocol/openid-connect/userInfo")),
                    oidcIntrospectionEndpoint.orElse(new IntrospectionEndpoint(new URL("http://keycloak:8080/auth/realms/oidc/protocol/openid-connect/introspect"), Optional.empty())),
                    oidcIntrospectionClaim.orElse("email"),
                    oidcAudience.orElse(new Aud("tmail")));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static JMAPOidcConfiguration parseConfiguration(Configuration configuration) {
        Optional<URL> oidcUserInfoUrl = Optional.ofNullable(configuration.getString(USERINFO_URL_PROPERTY, null))
            .map(Throwing.function(URL::new));
        Optional<URL> oidcIntrospectUrl = Optional.ofNullable(configuration.getString(INTROSPECT_URL_PROPERTY, null))
            .map(Throwing.function(URL::new));
        Optional<String> oidcIntrospectCreds = Optional.ofNullable(configuration.getString(INTROSPECT_CREDENTIALS_PROPERTY, null));
        Optional<Aud> oidcAudience = Optional.ofNullable(configuration.getString(AUDIENCE_PROPERTY, null)).map(Aud::new);
        Optional<String> oidcIntrospectionClaim = Optional.ofNullable(configuration.getString(CLAIM_PROPERTY, null));

        Optional<IntrospectionEndpoint> introspectionEndpoint = oidcIntrospectUrl.map(url -> new IntrospectionEndpoint(url, oidcIntrospectCreds));

        return JMAPOidcConfiguration.builder()
            .oidcUserInfoUrl(oidcUserInfoUrl)
            .oidcIntrospectionEndpoint(introspectionEndpoint)
            .oidcAudience(oidcAudience)
            .oidcClaim(oidcIntrospectionClaim)
            .build();
    }

    private final URL oidcUserInfoUrl;
    private final IntrospectionEndpoint introspectionEndpoint;
    private final String oidcClaim;
    private final Aud aud;

    JMAPOidcConfiguration(URL oidcUserInfoUrl, IntrospectionEndpoint introspectionEndpoint, String oidcClaim, Aud aud) {
        this.oidcUserInfoUrl = oidcUserInfoUrl;
        this.introspectionEndpoint = introspectionEndpoint;
        this.oidcClaim = oidcClaim;
        this.aud = aud;
    }

    public URL getOidcUserInfoUrl() {
        return oidcUserInfoUrl;
    }

    public String getOidcClaim() {
        return oidcClaim;
    }

    public IntrospectionEndpoint getIntrospectionEndpoint() {
        return introspectionEndpoint;
    }

    public Aud getAud() {
        return aud;
    }
}
