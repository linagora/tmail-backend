package com.linagora.tmail.configuration;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;

import com.linagora.tmail.AmqpUri;


public record OpenPaasConfiguration(URI apirUri,
                                    String adminUsername,
                                    String adminPassword,
                                    boolean trustAllSslCerts,
                                    Optional<ContactConsumerConfiguration> contactConsumerConfiguration,
                                    Optional<CardDavConfiguration> cardDavConfiguration) {

    public record ContactConsumerConfiguration(AmqpUri amqpUri,
                                               boolean quorumQueuesBypass) {
    }

    public OpenPaasConfiguration(URI apirUri, String adminUsername, String adminPassword, boolean trustAllSslCerts, ContactConsumerConfiguration contactConsumerConfiguration) {
        this(apirUri, adminUsername, adminPassword, trustAllSslCerts, Optional.of(contactConsumerConfiguration), Optional.empty());
    }

    public OpenPaasConfiguration(URI apirUri, String adminUsername, String adminPassword, boolean trustAllSslCerts, CardDavConfiguration cardDavConfiguration) {
        this(apirUri, adminUsername, adminPassword, trustAllSslCerts, Optional.empty(), Optional.of(cardDavConfiguration));
    }

    private static final String RABBITMQ_URI_PROPERTY = "rabbitmq.uri";
    private static final String OPENPAAS_API_URI = "openpaas.api.uri";
    private static final String OPENPAAS_ADMIN_USER_PROPERTY = "openpaas.admin.user";
    private static final String OPENPAAS_ADMIN_PASSWORD_PROPERTY = "openpaas.admin.password";
    private static final String OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_PROPERTY = "openpaas.rest.client.trust.all.ssl.certs";
    public static final boolean OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_DISABLED = false;
    private static final String OPENPAAS_QUEUES_QUORUM_BYPASS_PROPERTY = "openpaas.queues.quorum.bypass";
    public static final boolean OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED = false;
    public static final boolean OPENPAAS_QUEUES_QUORUM_BYPASS_ENABLED = true;

    public static OpenPaasConfiguration from(Configuration configuration) {
        URI openPaasApiUri = readApiUri(configuration);
        String adminUser = readAdminUsername(configuration);
        String adminPassword = readAdminPassword(configuration);
        boolean trustAllSslCerts = readTrustAllSslCerts(configuration);

        Optional<ContactConsumerConfiguration> contactConsumerConfiguration = readRabbitMqUri(configuration)
            .map(amqpUri -> new ContactConsumerConfiguration(amqpUri, readQuorumQueuesBypass(configuration)));

        Optional<CardDavConfiguration> cardDavConfiguration = CardDavConfiguration.maybeFrom(configuration);

        return new OpenPaasConfiguration(openPaasApiUri, adminUser, adminPassword, trustAllSslCerts, contactConsumerConfiguration, cardDavConfiguration);
    }

    public static boolean isConfiguredContactConsumer(Configuration configuration) {
        return readRabbitMqUri(configuration).isPresent();
    }

    public static Optional<AmqpUri> readRabbitMqUri(Configuration configuration) {
        String rabbitMqUri = configuration.getString(RABBITMQ_URI_PROPERTY);
        if (StringUtils.isBlank(rabbitMqUri)) {
           return Optional.empty();
        }

        try {
            return Optional.of(AmqpUri.from(URI.create(rabbitMqUri)));
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

}
