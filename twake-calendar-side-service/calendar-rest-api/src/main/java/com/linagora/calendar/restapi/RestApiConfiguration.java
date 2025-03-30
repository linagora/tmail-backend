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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

public class RestApiConfiguration {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        private Optional<URL> davdURL = Optional.empty();
        private Optional<URL> selfURL = Optional.empty();
        private Optional<URL> visioURL = Optional.empty();
        private Optional<Boolean> openpaasBackendTrustAllCerts = Optional.empty();
        private Optional<Boolean> sharingCalendarEnabled = Optional.empty();
        private Optional<Boolean> sharingAddressbookEnabled = Optional.empty();
        private Optional<Boolean> domainMembersAddressbookEnabled = Optional.empty();
        private Optional<URL> oidcUserInfoUrl = Optional.empty();
        private Optional<String> oidcIntrospectionClaim = Optional.empty();
        private Optional<String> defaultLanguage = Optional.empty();
        private Optional<String> defaultTimezone = Optional.empty();
        private Optional<JsonNode> defaultBusinessHours = Optional.empty();
        private Optional<Boolean> defaultUse24hFormat = Optional.empty();

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

        public Builder defaultLanguage(Optional<String> defaultLanguage) {
            this.defaultLanguage = defaultLanguage;
            return this;
        }

        public Builder defaultTimezone(Optional<String> defaultTimezone) {
            this.defaultTimezone = defaultTimezone;
            return this;
        }

        public Builder defaultBusinessHours(Optional<JsonNode> defaultBusinessHours) {
            this.defaultBusinessHours = defaultBusinessHours;
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

        public Builder selfUrl(Optional<URL> url) {
            this.selfURL = url;
            return this;
        }

        public Builder visioURL(Optional<URL> url) {
            this.visioURL = url;
            return this;
        }

        public Builder calendarSpaUrl(Optional<URL> url) {
            this.calendarSpaUrl = url;
            return this;
        }

        public Builder openpaasBackendURL(Optional<URL> url) {
            this.openpaasBackendURL = url;
            return this;
        }

        public Builder davURL(Optional<URL> url) {
            this.davdURL = url;
            return this;
        }

        public Builder openpaasBackendTrustAllCerts(Optional<Boolean> openpaasBackendTrustAllCerts) {
            this.openpaasBackendTrustAllCerts = openpaasBackendTrustAllCerts;
            return this;
        }

        public Builder defaultUse24hFormat(Optional<Boolean> defaultUse24hFormat) {
            this.defaultUse24hFormat = defaultUse24hFormat;
            return this;
        }

        public Builder enableCalendarSharing(Optional<Boolean> sharingCalendarEnabled) {
            this.sharingCalendarEnabled = sharingCalendarEnabled;
            return this;
        }

        public Builder sharingAddressbookEnabled(Optional<Boolean> sharingAddressbookEnabled) {
            this.sharingAddressbookEnabled = sharingAddressbookEnabled;
            return this;
        }

        public Builder domainMembersAddressbookEnabled(Optional<Boolean> domainMembersAddressbookEnabled) {
            this.domainMembersAddressbookEnabled = domainMembersAddressbookEnabled;
            return this;
        }

        public Builder oidcUserInfoUrl(Optional<URL> url) {
            this.oidcUserInfoUrl = url;
            return this;
        }

        public Builder oidcClaim(Optional<String> claim) {
            this.oidcIntrospectionClaim = claim;
            return this;
        }

        public RestApiConfiguration build() {
            try {
                ArrayNode arrayNode = defaultBusinessHours();

                return new RestApiConfiguration(port,
                    calendarSpaUrl.orElse(new URL("https://e-calendrier.avocat.fr")),
                    selfURL.orElse(new URL("https://twcalendar.linagora.com")),
                    openpaasBackendURL.orElse(new URL("https://openpaas.linagora.com")),
                    davdURL.orElse(new URL("https://dav.linagora.com")),
                    visioURL.orElse(new URL("https://jitsi.linagora.com")),
                    openpaasBackendTrustAllCerts.orElse(false),
                    jwtPrivatePath.orElse("classpath://jwt_privatekey"),
                    jwtPublicPath.orElse(ImmutableList.of("classpath://jwt_publickey")),
                    jwtValidity.orElse(Duration.ofHours(12)),
                    oidcUserInfoUrl.orElse(new URL("http://keycloak:8080/auth/realms/oidc/protocol/openid-connect/userInfo")),
                    oidcIntrospectionClaim.orElse("email"),
                    sharingCalendarEnabled.orElse(true),
                    sharingAddressbookEnabled.orElse(true),
                    domainMembersAddressbookEnabled.orElse(true),
                    defaultLanguage.orElse("en"),
                    defaultTimezone.orElse("Europe/Paris"),
                    defaultUse24hFormat.orElse(true),
                    defaultBusinessHours.orElse(arrayNode));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        private static ArrayNode defaultBusinessHours() {
            try {
                ObjectNode businessHours = OBJECT_MAPPER.createObjectNode();
                businessHours.put("start", "8:0");
                businessHours.put("end", "19:0");
                businessHours.put("daysOfWeek", OBJECT_MAPPER.readTree("[1,2,3,4,5]"));
                ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
                arrayNode.add(businessHours);
                return arrayNode;
            } catch (JsonProcessingException e) {
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
        Optional<URL> davURL = Optional.ofNullable(configuration.getString("dav.url", "https://dav.linagora.com"))
            .map(Throwing.function(URL::new));
        Optional<URL> selfURL = Optional.ofNullable(configuration.getString("dav.url", "https://dav.linagora.com"))
            .map(Throwing.function(URL::new));
        Optional<URL> visioURL = Optional.ofNullable(configuration.getString("visio.url", "https://dav.linagora.com"))
            .map(Throwing.function(URL::new));
        Optional<Boolean> openpaasBackendTrustAllCerts = Optional.ofNullable(configuration.getBoolean("openpaas.backend.trust.all.certificates", null));
        Optional<String> jwtPrivateKey = Optional.ofNullable(configuration.getString("jwt.key.private", null));
        Optional<List<String>> jwtPublicKey = Optional.ofNullable(configuration.getString("jwt.key.public", null))
            .map(s -> Splitter.on(',').splitToList(s));
        Optional<Duration> jwtValidity = Optional.ofNullable(configuration.getString("jwt.key.validity", null))
            .map(Duration::parse);
        Optional<URL> oidcIntrospectionUrl = Optional.ofNullable(configuration.getString("oidc.userInfo.url", null))
            .map(Throwing.function(URL::new));
        Optional<String> oidcIntrospectionClaim = Optional.ofNullable(configuration.getString("oidc.claim", null));
        Optional<Boolean> calendarSharingEnabled = Optional.ofNullable(configuration.getBoolean("calendar.sharing.enabled", null));
        Optional<Boolean> sharingAddressbookEnabled = Optional.ofNullable(configuration.getBoolean("contacts.sharing.enabled", null));
        Optional<Boolean> domainMembersAddressbookEnabled = Optional.ofNullable(configuration.getBoolean("domain.contacts.enabled", null));
        Optional<Boolean> defaultUse24hFormat = Optional.ofNullable(configuration.getBoolean("default.use.24h.format", null));
        Optional<String> defaultLanguage = Optional.ofNullable(configuration.getString("default.language", null));
        Optional<String> defaultTimezone = Optional.ofNullable(configuration.getString("default.timezone", null));
        ArrayNode arrayNode = readWorkingHours(configuration);

        return RestApiConfiguration.builder()
            .port(port)
            .calendarSpaUrl(calendarSpaUrl)
            .openpaasBackendURL(openpaasBackendURL)
            .davURL(davURL)
            .selfUrl(selfURL)
            .visioURL(visioURL)
            .openpaasBackendTrustAllCerts(openpaasBackendTrustAllCerts)
            .jwtPublicPath(jwtPublicKey)
            .jwtPrivatePath(jwtPrivateKey)
            .jwtValidity(jwtValidity)
            .oidcUserInfoUrl(oidcIntrospectionUrl)
            .oidcClaim(oidcIntrospectionClaim)
            .enableCalendarSharing(calendarSharingEnabled)
            .sharingAddressbookEnabled(sharingAddressbookEnabled)
            .domainMembersAddressbookEnabled(domainMembersAddressbookEnabled)
            .defaultLanguage(defaultLanguage)
            .defaultTimezone(defaultTimezone)
            .defaultUse24hFormat(defaultUse24hFormat)
            .defaultBusinessHours(Optional.of(arrayNode))
            .build();
    }

    private static ArrayNode readWorkingHours(Configuration configuration) {
        try {
            String defaultBusinessHoursStart = Optional.ofNullable(configuration.getString("default.business.hours.start", null)).orElse("8:0");
            String defaultBusinessHoursEnd = Optional.ofNullable(configuration.getString("default.business.hours.end", null)).orElse("19:0");
            JsonNode defaultBusinessWorkingDays = OBJECT_MAPPER.readTree(
                Optional.ofNullable(configuration.getStringArray("default.business.hours.daysOfWeek"))
                    .map(array -> Joiner.on(',').join(array))
                    .map(s -> "[" + s + "]")
                    .orElse("[1,2,3,4,5,6]"));

            ObjectNode defaultBusinessHours = OBJECT_MAPPER.createObjectNode();
            defaultBusinessHours.put("start", defaultBusinessHoursStart);
            defaultBusinessHours.put("end", defaultBusinessHoursEnd);
            defaultBusinessHours.put("daysOfWeek", defaultBusinessWorkingDays);

            ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
            arrayNode.add(defaultBusinessHours);
            return arrayNode;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private final Optional<Port> port;
    private final URL calendarSpaUrl;
    private final URL selfUrl;
    private final URL openpaasBackendURL;
    private final URL davURL;
    private final URL visioURL;
    private final boolean openpaasBackendTrustAllCerts;
    private final String jwtPrivatePath;
    private final List<String> jwtPublicPath;
    private final Duration jwtValidity;
    private final URL oidcIntrospectionUrl;
    private final String oidcClaim;
    private final boolean calendarSharingEnabled;
    private final boolean sharingContactsEnabled;
    private final boolean domainMembersAddressbookEnabled;
    private final String defaultLanguage;
    private final String defaultTimezone;
    private final boolean defaultUse24hFormat;
    private final JsonNode defaultBusinessHours;

    @VisibleForTesting
    RestApiConfiguration(Optional<Port> port, URL calendarSpaUrl, URL selfUrl, URL openpaasBackendURL, URL davURL, URL visioURL, boolean openpaasBackendTrustAllCerts,
                         String jwtPrivatePath, List<String> jwtPublicPath, Duration jwtValidity, URL oidcIntrospectionUrl,
                         String oidcIntrospectionClaim, boolean calendarSharingENabled, boolean sharingCalendarEnabled, boolean domainMembersAddressbookEnabled,
                         String defaultLanguage, String defaultTimezone, boolean defaultUse24hFormat, JsonNode defaultBusinessHours) {
        this.port = port;
        this.calendarSpaUrl = calendarSpaUrl;
        this.selfUrl = selfUrl;
        this.openpaasBackendURL = openpaasBackendURL;
        this.davURL = davURL;
        this.visioURL = visioURL;
        this.openpaasBackendTrustAllCerts = openpaasBackendTrustAllCerts;
        this.jwtPrivatePath = jwtPrivatePath;
        this.jwtPublicPath = jwtPublicPath;
        this.jwtValidity = jwtValidity;
        this.oidcIntrospectionUrl = oidcIntrospectionUrl;
        this.oidcClaim = oidcIntrospectionClaim;
        this.calendarSharingEnabled = calendarSharingENabled;
        this.sharingContactsEnabled = sharingCalendarEnabled;
        this.domainMembersAddressbookEnabled = domainMembersAddressbookEnabled;
        this.defaultLanguage = defaultLanguage;
        this.defaultTimezone = defaultTimezone;
        this.defaultUse24hFormat = defaultUse24hFormat;
        this.defaultBusinessHours = defaultBusinessHours;
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

    public String getOidcClaim() {
        return oidcClaim;
    }

    public URL getDavURL() {
        return davURL;
    }

    public URL getSelfUrl() {
        return selfUrl;
    }

    public boolean isCalendarSharingEnabled() {
        return calendarSharingEnabled;
    }

    public URL getVisioURL() {
        return visioURL;
    }

    public boolean isSharingContactsEnabled() {
        return sharingContactsEnabled;
    }

    public boolean isDomainMembersAddressbookEnabled() {
        return domainMembersAddressbookEnabled;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public String getDefaultTimezone() {
        return defaultTimezone;
    }

    public JsonNode getDefaultBusinessHours() {
        return defaultBusinessHours;
    }

    public boolean isDefaultUse24hFormat() {
        return defaultUse24hFormat;
    }
}
