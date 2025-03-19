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

package com.linagora.calendar.restapi;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.util.Port;
import org.apache.james.utils.PropertiesProvider;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

public class RestApiConfiguration {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<Port> port = Optional.empty();
        private Optional<String> jwtPrivatePath = Optional.empty();
        private Optional<List<String>> jwtPublicPath = Optional.empty();
        private Optional<Duration> jwtValidity = Optional.empty();
        private Optional<URL> calendarSpaUrl = Optional.empty();
        private Optional<URL> openpaasBackendURL = Optional.empty();
        private Optional<Boolean> openpaasBackendTrustAllCerts = Optional.empty();
        private Optional<URL> oidcIntrospectionUrl = Optional.empty();
        private Optional<String> oidcIntrospectionAuth = Optional.empty();
        private Optional<String> oidcIntrospectionClaim = Optional.empty();

        private Builder() {

        }

        public Builder port(Port port) {
            this.port = Optional.of(port);
            return this;
        }

        public Builder jwtPrivatePath(Optional<String> jwtPrivatePath) {
            this.jwtPrivatePath = jwtPrivatePath;
            return this;
        }

        public Builder jwtPublicPath(Optional<List<String>> jwtPublicPath) {
            this.jwtPublicPath = jwtPublicPath;
            return this;
        }

        public Builder jwtValidity(Optional<Duration> jwtValidity) {
            this.jwtValidity = jwtValidity;
            return this;
        }

        public Builder port(Optional<Port> port) {
            this.port = port;
            return this;
        }

        public Builder calendarSpaUrl(URL url) {
            this.calendarSpaUrl = Optional.of(url);
            return this;
        }

        public Builder calendarSpaUrl(Optional<URL> url) {
            this.calendarSpaUrl = url;
            return this;
        }

        public Builder openpaasBackendURL(URL url) {
            this.openpaasBackendURL = Optional.of(url);
            return this;
        }

        public Builder openpaasBackendURL(Optional<URL> url) {
            this.openpaasBackendURL = url;
            return this;
        }

        public Builder openpaasBackendTrustAllCerts(Optional<Boolean> openpaasBackendTrustAllCerts) {
            this.openpaasBackendTrustAllCerts = openpaasBackendTrustAllCerts;
            return this;
        }

        public Builder oidcIntrospectionUrl(Optional<URL> url) {
            this.oidcIntrospectionUrl = url;
            return this;
        }

        public Builder oidcIntrospectionAuth(Optional<String> auth) {
            this.oidcIntrospectionAuth = auth;
            return this;
        }

        public Builder oidcIntrospectionClaim(Optional<String> claim) {
            this.oidcIntrospectionClaim = claim;
            return this;
        }

        public RestApiConfiguration build() {
            try {
                return new RestApiConfiguration(port,
                    calendarSpaUrl.orElse(new URL("https://e-calendrier.avocat.fr")),
                    openpaasBackendURL.orElse(new URL("https://openpaas.linagora.com")),
                    openpaasBackendTrustAllCerts.orElse(false),
                    jwtPrivatePath.orElse("classpath://jwt_privatekey"),
                    jwtPublicPath.orElse(ImmutableList.of("classpath://jwt_publickey")),
                    jwtValidity.orElse(Duration.ofHours(12)),
                    oidcIntrospectionUrl.orElse(new URL("http://keycloak:8080/auth/realms/oidc/protocol/openid-connect/token/introspect")),
                    oidcIntrospectionAuth,
                    oidcIntrospectionClaim.orElse("email"));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static RestApiConfiguration parseConfiguration(PropertiesProvider propertiesProvider) throws FileNotFoundException, ConfigurationException {
        Configuration configuration = propertiesProvider.getConfiguration("configuration");
        return parseConfiguration(configuration);
    }

    public static RestApiConfiguration parseConfiguration(Configuration configuration) {
        Optional<Port> port = Optional.ofNullable(configuration.getInteger("rest.api.port", null))
            .map(Port::of);
        Optional<URL> calendarSpaUrl = Optional.ofNullable(configuration.getString("spa.calendar.url", null))
            .map(Throwing.function(URL::new));
        Optional<URL> openpaasBackendURL = Optional.ofNullable(configuration.getString("openpaas.backend.url", null))
            .map(Throwing.function(URL::new));
        Optional<Boolean> openpaasBackendTrustAllCerts = Optional.ofNullable(configuration.getBoolean("openpaas.backend.trust.all.certificates", null));
        Optional<String> jwtPrivateKey = Optional.ofNullable(configuration.getString("jwt.key.private", null));
        Optional<List<String>> jwtPublicKey = Optional.ofNullable(configuration.getString("jwt.key.public", null))
            .map(s -> Splitter.on(',').splitToList(s));
        Optional<Duration> jwtValidity = Optional.ofNullable(configuration.getString("jwt.key.validity", null))
            .map(Duration::parse);
        Optional<URL> oidcIntrospectionUrl = Optional.ofNullable(configuration.getString("oidc.introspection.url", null))
            .map(Throwing.function(URL::new));
        Optional<String> oidcIntrospectionAuth = Optional.ofNullable(configuration.getString("oidc.introspection.auth", null));
        Optional<String> oidcIntrospectionClaim = Optional.ofNullable(configuration.getString("oidc.introspection.claim", null));

        return RestApiConfiguration.builder()
            .port(port)
            .calendarSpaUrl(calendarSpaUrl)
            .openpaasBackendURL(openpaasBackendURL)
            .openpaasBackendTrustAllCerts(openpaasBackendTrustAllCerts)
            .jwtPublicPath(jwtPublicKey)
            .jwtPrivatePath(jwtPrivateKey)
            .jwtValidity(jwtValidity)
            .oidcIntrospectionUrl(oidcIntrospectionUrl)
            .oidcIntrospectionAuth(oidcIntrospectionAuth)
            .oidcIntrospectionClaim(oidcIntrospectionClaim)
            .build();
    }

    private final Optional<Port> port;
    private final URL calendarSpaUrl;
    private final URL openpaasBackendURL;
    private final boolean openpaasBackendTrustAllCerts;
    private final String jwtPrivatePath;
    private final List<String> jwtPublicPath;
    private final Duration jwtValidity;
    private final URL oidcIntrospectionUrl;
    private final Optional<String> oidcIntrospectionAuth;
    private final String oidcIntrospectionClaim;

    @VisibleForTesting
    RestApiConfiguration(Optional<Port> port, URL clandarSpaUrl, URL openpaasBackendURL, boolean openpaasBackendTrustAllCerts,
                         String jwtPrivatePath, List<String> jwtPublicPath, Duration jwtValidity, URL oidcIntrospectionUrl,
                         Optional<String> oidcIntrospectionAuth, String oidcIntrospectionClaim) {
        this.port = port;
        this.calendarSpaUrl = clandarSpaUrl;
        this.openpaasBackendURL = openpaasBackendURL;
        this.openpaasBackendTrustAllCerts = openpaasBackendTrustAllCerts;
        this.jwtPrivatePath = jwtPrivatePath;
        this.jwtPublicPath = jwtPublicPath;
        this.jwtValidity = jwtValidity;
        this.oidcIntrospectionUrl = oidcIntrospectionUrl;
        this.oidcIntrospectionAuth = oidcIntrospectionAuth;
        this.oidcIntrospectionClaim = oidcIntrospectionClaim;
    }

    public Optional<Port> getPort() {
        return port;
    }

    public URL getCalendarSpaUrl() {
        return calendarSpaUrl;
    }

    public URL getOpenpaasBackendURL() {
        return openpaasBackendURL;
    }

    public boolean openpaasBackendTrustAllCerts() {
        return openpaasBackendTrustAllCerts;
    }

    public String getJwtPrivatePath() {
        return jwtPrivatePath;
    }

    public List<String> getJwtPublicPath() {
        return jwtPublicPath;
    }

    public Duration getJwtValidity() {
        return jwtValidity;
    }

    public URL getOidcIntrospectionUrl() {
        return oidcIntrospectionUrl;
    }

    public Optional<String> getOidcIntrospectionAuth() {
        return oidcIntrospectionAuth;
    }

    public String getOidcIntrospectionClaim() {
        return oidcIntrospectionClaim;
    }
}
