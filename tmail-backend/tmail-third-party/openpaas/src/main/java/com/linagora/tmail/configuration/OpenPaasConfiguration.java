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

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.util.DurationParser;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.linagora.tmail.AmqpUri;


public record OpenPaasConfiguration(URI apirUri,
                                    String adminUsername,
                                    String adminPassword,
                                    boolean trustAllSslCerts,
                                    Duration responseTimeout,
                                    Optional<ContactConsumerConfiguration> contactConsumerConfiguration,
                                    Optional<DavConfiguration> davConfiguration) {

    public record ContactConsumerConfiguration(List<AmqpUri> amqpUri,
                                               boolean quorumQueuesBypass) {
    }

    @VisibleForTesting
    public OpenPaasConfiguration(URI apirUri, String adminUsername, String adminPassword, boolean trustAllSslCerts, Duration responseTimeout, ContactConsumerConfiguration contactConsumerConfiguration) {
        this(apirUri, adminUsername, adminPassword, trustAllSslCerts, responseTimeout, Optional.of(contactConsumerConfiguration), Optional.empty());
    }

    @VisibleForTesting
    public OpenPaasConfiguration(URI apirUri, String adminUsername, String adminPassword, boolean trustAllSslCerts, ContactConsumerConfiguration contactConsumerConfiguration) {
        this(apirUri, adminUsername, adminPassword, trustAllSslCerts, DEFAULT_RESPONSE_TIMEOUT, Optional.of(contactConsumerConfiguration), Optional.empty());
    }

    @VisibleForTesting
    public OpenPaasConfiguration(URI apirUri, String adminUsername, String adminPassword, boolean trustAllSslCerts, DavConfiguration davConfiguration) {
        this(apirUri, adminUsername, adminPassword, trustAllSslCerts, DEFAULT_RESPONSE_TIMEOUT, Optional.empty(), Optional.of(davConfiguration));
    }

    private static final String RABBITMQ_URI_PROPERTY = "rabbitmq.uri";
    private static final String OPENPAAS_API_URI = "openpaas.api.uri";
    private static final String OPENPAAS_ADMIN_USER_PROPERTY = "openpaas.admin.user";
    private static final String OPENPAAS_ADMIN_PASSWORD_PROPERTY = "openpaas.admin.password";
    private static final String OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_PROPERTY = "openpaas.rest.client.trust.all.ssl.certs";
    private static final String OPENPAAS_REST_CLIENT_RESPONSE_TIMEOUT_PROPERTY = "openpaas.rest.client.response.timeout";
    public static final boolean OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_DISABLED = false;
    private static final String OPENPAAS_QUEUES_QUORUM_BYPASS_PROPERTY = "openpaas.queues.quorum.bypass";
    public static final boolean OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED = false;
    public static final boolean OPENPAAS_QUEUES_QUORUM_BYPASS_ENABLED = true;
    public static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(30);

    public static OpenPaasConfiguration from(Configuration configuration) {
        URI openPaasApiUri = readApiUri(configuration);
        String adminUser = readAdminUsername(configuration);
        String adminPassword = readAdminPassword(configuration);
        boolean trustAllSslCerts = readTrustAllSslCerts(configuration);
        Duration responseTimeout = readOpenPaasClientTimeout(configuration);

        Optional<ContactConsumerConfiguration> contactConsumerConfiguration = readRabbitMqUri(configuration)
            .map(amqpUri -> new ContactConsumerConfiguration(amqpUri, readQuorumQueuesBypass(configuration)));

        Optional<DavConfiguration> davConfiguration = DavConfiguration.maybeFrom(configuration);

        return new OpenPaasConfiguration(openPaasApiUri, adminUser, adminPassword, trustAllSslCerts, responseTimeout, contactConsumerConfiguration, davConfiguration);
    }

    public static boolean isConfiguredContactConsumer(Configuration configuration) {
        return readRabbitMqUri(configuration).isPresent();
    }

    public static Optional<List<AmqpUri>> readRabbitMqUri(Configuration configuration) {
        String rabbitMqUri = configuration.getString(RABBITMQ_URI_PROPERTY);
        if (StringUtils.isBlank(rabbitMqUri)) {
           return Optional.empty();
        }

        try {
            return Optional.of(Splitter.on(',').trimResults()
                .splitToStream(rabbitMqUri)
                .map(AmqpUri::from)
                .collect(ImmutableList.toImmutableList()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid RabbitMQ URI in openpaas.properties.");
        }
    }

    private static URI readApiUri(Configuration configuration) {
        String openPaasApiUri = configuration.getString(OPENPAAS_API_URI);
        if (StringUtils.isBlank(openPaasApiUri)) {
            throw new IllegalStateException("OpenPaas API URI not specified.");
        }

        try {
            return validateURI(URI.create(openPaasApiUri));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid OpenPaas API URI in openpaas.properties.");
        }
    }

    private static URI validateURI(URI uri) {
        try {
            // Otherwise, BAD_URI would be considered a valid URI.
            uri.toURL();
            return uri;
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Bad URI!", e);
        }
    }

    private static String readAdminUsername(Configuration configuration) {
        String openPaasAdminUser = configuration.getString(OPENPAAS_ADMIN_USER_PROPERTY);

        if (StringUtils.isBlank(openPaasAdminUser)) {
            throw new IllegalStateException("OpenPaas admin user not specified.");
        }

        return openPaasAdminUser;
    }

    private static String readAdminPassword(Configuration configuration) {
        String openPaasAdminPassword = configuration.getString(OPENPAAS_ADMIN_PASSWORD_PROPERTY);
        if (StringUtils.isBlank(openPaasAdminPassword)) {
            throw new IllegalStateException("OpenPaas admin password not specified.");
        }

        return openPaasAdminPassword;
    }

    private static boolean readTrustAllSslCerts(Configuration configuration) {
        return Optional.ofNullable(configuration.getBoolean(OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_PROPERTY, null))
            .orElse(OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_DISABLED);
    }

    private static boolean readQuorumQueuesBypass(Configuration configuration) {
        return configuration.getBoolean(OPENPAAS_QUEUES_QUORUM_BYPASS_PROPERTY, OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED);
    }

    private static Duration readOpenPaasClientTimeout(Configuration configuration) {
        return Optional.ofNullable(configuration.getString(OPENPAAS_REST_CLIENT_RESPONSE_TIMEOUT_PROPERTY, null))
            .map(value -> DurationParser.parse(value, ChronoUnit.MILLIS))
            .orElse(DEFAULT_RESPONSE_TIMEOUT);
    }

}
