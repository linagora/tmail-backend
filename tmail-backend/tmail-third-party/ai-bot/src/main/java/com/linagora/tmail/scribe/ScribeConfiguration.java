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
package com.linagora.tmail.scribe;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public record ScribeConfiguration(String authorizationToken,
                                  Optional<URL> baseURLOpt,
                                  boolean trustAllCertificates) {

    public static final String API_KEY_PARAMETER_NAME = "scribe.token";
    public static final String BASE_URL_PARAMETER_NAME = "scribe.url";
    public static final String TRUST_ALL_CERTIFICATES_PARAMETER_NAME = "scribe.ssl.trust.all.certs";

    public static final String LEGACY_API_KEY_PARAMETER_NAME = "apiKey";
    public static final String LEGACY_BASE_URL_PARAMETER_NAME = "baseURL";

    public ScribeConfiguration {
        Preconditions.checkNotNull(authorizationToken);
        Preconditions.checkNotNull(baseURLOpt);
    }

    public static ScribeConfiguration from(Configuration configuration) throws IllegalArgumentException {
        String apiKeyParam = Optional.ofNullable(configuration.getString(API_KEY_PARAMETER_NAME, null))
            .or(() -> Optional.ofNullable(configuration.getString(LEGACY_API_KEY_PARAMETER_NAME, null)))
            .orElseThrow(() -> new IllegalArgumentException(
                "No value for " + API_KEY_PARAMETER_NAME + " parameter was provided. " +
                "(Fallback to legacy property " + LEGACY_API_KEY_PARAMETER_NAME + " also failed)."));

        String baseUrlParam = Optional.ofNullable(configuration.getString(BASE_URL_PARAMETER_NAME, null))
            .or(() -> Optional.ofNullable(configuration.getString(LEGACY_BASE_URL_PARAMETER_NAME, null)))
            .orElseThrow(() -> new IllegalArgumentException(
                "No value for " + BASE_URL_PARAMETER_NAME + " parameter was provided. " +
                "(Fallback to legacy property " + LEGACY_BASE_URL_PARAMETER_NAME + " also failed)."));

        Optional<URL> baseURLOpt = Optional.ofNullable(baseUrlParam)
            .filter(baseUrlString -> !Strings.isNullOrEmpty(baseUrlString))
            .flatMap(ScribeConfiguration::baseURLStringToURL);

        boolean trustAllCertificatesParam = configuration.getBoolean(TRUST_ALL_CERTIFICATES_PARAMETER_NAME, true);

        try {
            return new ScribeConfiguration(apiKeyParam, baseURLOpt, trustAllCertificatesParam);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<URL> baseURLStringToURL(String baseUrlString) {
        try {
            return Optional.of(URI.create(baseUrlString).toURL());
        } catch (IllegalArgumentException | MalformedURLException e) {
            throw new RuntimeException("Invalid Scribe API base URL: " + baseUrlString, e);
        }
    }
}
