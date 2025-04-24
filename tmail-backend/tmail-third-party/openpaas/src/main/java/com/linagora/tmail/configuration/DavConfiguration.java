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

package com.linagora.tmail.configuration;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.UsernamePasswordCredentials;

import com.google.common.base.Preconditions;

public record DavConfiguration(UsernamePasswordCredentials adminCredential,
                               URI baseUrl,
                               boolean trustAllSslCerts,
                               Optional<Duration> responseTimeout) {
    static final boolean CLIENT_TRUST_ALL_SSL_CERTS_DISABLED = false;
    static final String DAV_API_URI_PROPERTY = "dav.api.uri";
    static final String DAV_ADMIN_USER_PROPERTY = "dav.admin.user";
    static final String DAV_ADMIN_PASSWORD_PROPERTY = "dav.admin.password";
    static final String DAV_REST_CLIENT_TRUST_ALL_SSL_CERTS_PROPERTY = "dav.rest.client.trust.all.ssl.certs";
    static final String DAV_REST_CLIENT_RESPONSE_TIMEOUT_PROPERTY = "dav.rest.client.response.timeout";

    public static Optional<DavConfiguration> maybeFrom(Configuration configuration) {
        if (isConfigured(configuration)) {
            return Optional.of(from(configuration));
        }
        return Optional.empty();
    }

    public static DavConfiguration from(Configuration configuration) {
        String adminUser = configuration.getString(DAV_ADMIN_USER_PROPERTY, null);
        String adminPassword = configuration.getString(DAV_ADMIN_PASSWORD_PROPERTY, null);

        Preconditions.checkArgument(StringUtils.isNotEmpty(adminUser), DAV_ADMIN_USER_PROPERTY + " should not be empty");
        Preconditions.checkArgument(StringUtils.isNotEmpty(adminPassword), DAV_ADMIN_PASSWORD_PROPERTY + " should not be empty");
        UsernamePasswordCredentials adminCredential = new UsernamePasswordCredentials(adminUser, adminPassword);

        String baseUrlAsString = configuration.getString(DAV_API_URI_PROPERTY);
        Preconditions.checkArgument(StringUtils.isNotEmpty(baseUrlAsString), DAV_API_URI_PROPERTY + " should not be empty");
        URI baseUrl = URI.create(baseUrlAsString);

        boolean trustAllSslCerts = configuration.getBoolean(DAV_REST_CLIENT_TRUST_ALL_SSL_CERTS_PROPERTY, CLIENT_TRUST_ALL_SSL_CERTS_DISABLED);
        Optional<Duration> responseTimeout = Optional.ofNullable(configuration.getLong(
                DAV_REST_CLIENT_RESPONSE_TIMEOUT_PROPERTY, null))
            .map(durationAsMilliseconds -> {
                Preconditions.checkArgument(durationAsMilliseconds > 0, "Response timeout should not be negative");
                return Duration.ofMillis(durationAsMilliseconds);
            });
        return new DavConfiguration(adminCredential, baseUrl, trustAllSslCerts, responseTimeout);
    }

    public static boolean isConfigured(Configuration configuration) {
        String baseUrl = configuration.getString(DAV_API_URI_PROPERTY, null);
        return StringUtils.isNotEmpty(baseUrl);
    }
}