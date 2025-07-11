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

package com.linagora.tmail.mailet.rag;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class RagConfig {
    public static String AUTHORIZATION_TOKEN_PARAMETER_NAME = "ragondin.Token";
    public static String TRUST_ALL_CERTIFICATES_PARAMETER_NAME = "ragondin.ssl.trust.all.certs";
    public static String BASE_URL_PARAMETER_NAME = "ragondin.url";
    public static String PARTITION_PATTERN_PARAMETER_NAME = "ragondin.partition.pattern";
    public static final String DEFAULT_PARTITION_PATTERN = "{localPart}.twake.{domainName}";
    private final String authorizationToken;
    private final Optional<URL> baseURLOpt;
    private final Boolean trustAllCertificates;
    private final String partitionPattern;

    public RagConfig(String authorizationToken, Boolean trustAllCertificates, Optional<URL> baseURLOpt, String partitionPattern) {
        Preconditions.checkNotNull(authorizationToken);
        Preconditions.checkNotNull(trustAllCertificates);
        Preconditions.checkNotNull(baseURLOpt);
        Preconditions.checkNotNull(partitionPattern);

        this.authorizationToken = authorizationToken;
        this.baseURLOpt = baseURLOpt;
        this.trustAllCertificates = trustAllCertificates;
        this.partitionPattern = partitionPattern;

    }

    public static RagConfig from(Configuration configuration) throws IllegalArgumentException {
        String tokenParam = Optional.ofNullable(configuration.getString(AUTHORIZATION_TOKEN_PARAMETER_NAME, null))
            .orElseThrow(() ->  new IllegalArgumentException("No value for " + AUTHORIZATION_TOKEN_PARAMETER_NAME + " parameter was provided."));
        Boolean trustAllCertificatesParam = configuration.getBoolean(TRUST_ALL_CERTIFICATES_PARAMETER_NAME, true);

        String baseUrlParam = Optional.ofNullable(configuration.getString(BASE_URL_PARAMETER_NAME,null))
            .orElseThrow(() ->  new IllegalArgumentException("No value for " + BASE_URL_PARAMETER_NAME + " parameter was provided."));
        String partitionPattern = Optional.ofNullable(configuration.getString(PARTITION_PATTERN_PARAMETER_NAME))
            .filter(pattern -> !Strings.isNullOrEmpty(pattern))
            .orElse(DEFAULT_PARTITION_PATTERN);
        Optional<URL> baseURLOpt = Optional.ofNullable(baseUrlParam)
            .filter(baseUrlString -> !Strings.isNullOrEmpty(baseUrlString))
            .flatMap(RagConfig::baseURLStringToURL);

        return new RagConfig(tokenParam, trustAllCertificatesParam, baseURLOpt, partitionPattern);
    }

    private static Optional<URL> baseURLStringToURL(String baseUrlString) {
        try {
            return Optional.of(URI.create(baseUrlString).toURL());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid RAG API base URL", e);
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
        RagConfig that = (RagConfig) o;
        return Objects.equals(authorizationToken, that.authorizationToken) &&
            Objects.equals(trustAllCertificates, that.trustAllCertificates) &&
            Objects.equals(partitionPattern, that.partitionPattern) &&
            Objects.equals(Optional.ofNullable(baseURLOpt).map(opt -> opt.map(URL::toString)),
                Optional.ofNullable(that.baseURLOpt).map(opt -> opt.map(URL::toString)));
    }

    @Override
    public int hashCode() {
        return Objects.hash(authorizationToken, trustAllCertificates,
            Optional.ofNullable(baseURLOpt).map(opt -> opt.map(URL::toString)), partitionPattern);
    }

    public String getAuthorizationToken() {
        return authorizationToken;
    }

    public Optional<URL> getBaseURLOpt() {
        return baseURLOpt;
    }

    public Boolean getTrustAllCertificates() {
        return trustAllCertificates;
    }

    public String getPartitionPattern() {
        return partitionPattern;
    }
}
