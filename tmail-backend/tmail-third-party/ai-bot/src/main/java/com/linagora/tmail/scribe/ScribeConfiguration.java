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
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class ScribeConfiguration {
    public static String API_KEY_PARAMETER_NAME = "scribe.token";
    public static String BASE_URL_PARAMETER_NAME = "scribe.url";
    public static final String TRUST_ALL_CERTIFICATES_PARAMETER_NAME = "scribe.ssl.trust.all.certs";

    private final String authorizationToken;
    private final Optional<URL> baseURLOpt;
    private final boolean trustAllCertificates;

    public ScribeConfiguration(String token, Optional<URL> baseURLOpt, boolean trustAllCertificates) {
        Preconditions.checkNotNull(token);
        Preconditions.checkNotNull(baseURLOpt);
        Preconditions.checkNotNull(trustAllCertificates);

        this.authorizationToken = token;
        this.baseURLOpt = baseURLOpt;
        this.trustAllCertificates = trustAllCertificates;
    }

    public static ScribeConfiguration from(Configuration configuration) throws IllegalArgumentException {
        String apiKeyParam = Optional.ofNullable(configuration.getString(API_KEY_PARAMETER_NAME, null))
            .orElseThrow(() ->  new IllegalArgumentException("No value for " + API_KEY_PARAMETER_NAME + " parameter was provided."));
        String baseUrlParam = Optional.ofNullable(configuration.getString(BASE_URL_PARAMETER_NAME,null))
            .orElseThrow(() ->  new IllegalArgumentException("No value for " + BASE_URL_PARAMETER_NAME + " parameter was provided."));

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
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid Scribe API base URL", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ScribeConfiguration that = (ScribeConfiguration) o;
        return Objects.equals(authorizationToken, that.authorizationToken) &&
            Objects.equals(Optional.ofNullable(baseURLOpt).map(opt -> opt.map(URL::toString)),
                Optional.ofNullable(that.baseURLOpt).map(opt -> opt.map(URL::toString))) &&
            Objects.equals(trustAllCertificates, that.trustAllCertificates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authorizationToken,
            Optional.ofNullable(baseURLOpt).map(opt -> opt.map(URL::toString)),
            trustAllCertificates);
    }

    public String getAuthorizationToken() {
        return authorizationToken;
    }

    public boolean getTrustAllCertificates() {
        return trustAllCertificates;
    }

    public Optional<URL> getBaseURLOpt() {
        return baseURLOpt;
    }
}
