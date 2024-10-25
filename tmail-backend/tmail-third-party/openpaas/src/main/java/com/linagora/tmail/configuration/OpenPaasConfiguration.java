package com.linagora.tmail.configuration;

import java.net.URI;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.linagora.tmail.AmqpUri;

public record OpenPaasConfiguration(Optional<AmqpUri> maybeRabbitMqUri, URI openpaasApiUri, String adminUser, String adminPassword) {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenPaasConfiguration.class);
    private static final String RABBITMQ_URI_PROPERTY = "rabbitmq.uri";
    private static final String OPENPAAS_API_URI = "openpaas.api.uri";
    private static final String OPENPAAS_ADMIN_USER_PROPERTY = "openpaas.admin.user";
    private static final String OPENPAAS_ADMIN_PASSWORD_PROPERTY = "openpaas.admin.password";

    public static OpenPaasConfiguration from(Configuration configuration) {
        Optional<AmqpUri> maybeRabbitMqUri = readRabbitMqUri(configuration);
        URI openPaasApiUri = readOpenPaasApiUri(configuration);
        String adminUser = readAdminUser(configuration);
        String adminPassword = readAdminPassword(configuration);

        return new OpenPaasConfiguration(maybeRabbitMqUri, openPaasApiUri, adminUser, adminPassword);
    }

    private static Optional<AmqpUri> readRabbitMqUri(Configuration configuration) {
        String rabbitMqUri = configuration.getString(RABBITMQ_URI_PROPERTY);
        if (Strings.isNullOrEmpty(rabbitMqUri)) {
            LOGGER.debug("RabbitMQ URI not defined in openpaas.properties.");
            return Optional.empty();
        }

        try {
            return AmqpUri.from(URI.create(rabbitMqUri)).asOptional();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid RabbitMQ URI in openpaas.properties.");
        }
    }

    private static URI readOpenPaasApiUri(Configuration configuration) {
        String openPaasApiUri = configuration.getString(OPENPAAS_API_URI);
        if (Strings.isNullOrEmpty(openPaasApiUri)) {
            throw new IllegalStateException("OpenPaas API URI not specified.");
        }

        try {
            return URI.create(openPaasApiUri);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid OpenPaas API URI in openpaas.properties.");
        }
    }

    private static String readAdminUser(Configuration configuration) {
        String openPaasAdminUser = configuration.getString(OPENPAAS_ADMIN_USER_PROPERTY);
        if (Strings.isNullOrEmpty(openPaasAdminUser)) {
            throw new IllegalStateException("OpenPaas admin user not specified.");
        }

        return openPaasAdminUser;
    }

    private static String readAdminPassword(Configuration configuration) {
        String openPaasAdminPassword = configuration.getString(OPENPAAS_ADMIN_PASSWORD_PROPERTY);
        if (Strings.isNullOrEmpty(openPaasAdminPassword)) {
            throw new IllegalStateException("OpenPaas admin password not specified.");
        }

        return openPaasAdminPassword;
    }

}
