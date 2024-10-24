package com.linagora.tmail;

import java.net.URI;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

public record OpenPaasConfiguration(Optional<AmqpUri> maybeRabbitMqUri, Optional<URI> openpaasApiUri, Optional<String> maybeAdminUser, Optional<String> maybeAdminPassword) {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenPaasConfiguration.class);
    private static final String RABBITMQ_URI_PROPERTY = "rabbitmq.uri";
    private static final String OPENPAAS_API_URI = "openpaas.api.uri";
    private static final String OPENPAAS_ADMIN_USER_PROPERTY = "openpaas.admin.user";
    private static final String OPENPAAS_ADMIN_PASSWORD_PROPERTY = "openpaas.admin.password";

    public static OpenPaasConfiguration from(Configuration configuration) {
        Optional<AmqpUri> maybeRabbitMqUri = readRabbitMqUri(configuration);
        Optional<URI> maybeOpenPaasApiUri = readOpenPaasApiUri(configuration);
        Optional<String> maybeAdminUser = readAdminUser(configuration);
        Optional<String> maybeAdminPassword = readAdminPassword(configuration);

        return new OpenPaasConfiguration(maybeRabbitMqUri, maybeOpenPaasApiUri, maybeAdminUser, maybeAdminPassword);
    }

    private static Optional<AmqpUri> readRabbitMqUri(Configuration configuration) {
        String rabbitMqUri = configuration.getString(RABBITMQ_URI_PROPERTY);
        if (Strings.isNullOrEmpty(rabbitMqUri)) {
            LOGGER.debug("RabbitMQ URI not defined in openpaas.properties, falling back to rabbitmq.properties.");
            return Optional.empty();
        }

        try {
            return AmqpUri.from(URI.create(rabbitMqUri)).asOptional();
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid RabbitMQ URI in openpaas.properties, falling back to rabbitmq.properties.");
            return Optional.empty();
        }
    }

    private static Optional<URI> readOpenPaasApiUri(Configuration configuration) {
        String openPaasApiUri = configuration.getString(OPENPAAS_API_URI);
        if (Strings.isNullOrEmpty(openPaasApiUri)) {
            LOGGER.debug("OpenPaas API URI not specified.");
            return Optional.empty();
        }

        try {
            return Optional.of(URI.create(openPaasApiUri));
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid OpenPaas API URI in openpaas.properties.");
            return Optional.empty();
        }
    }

    private static Optional<String> readAdminUser(Configuration configuration) {
        String openPaasAdminUser = configuration.getString(OPENPAAS_ADMIN_USER_PROPERTY);
        if (Strings.isNullOrEmpty(openPaasAdminUser)) {
            LOGGER.debug("OpenPaas admin user not specified.");
            return Optional.empty();
        }

        return Optional.of(openPaasAdminUser);
    }

    private static Optional<String> readAdminPassword(Configuration configuration) {
        String openPaasAdminPassword = configuration.getString(OPENPAAS_ADMIN_PASSWORD_PROPERTY);
        if (Strings.isNullOrEmpty(openPaasAdminPassword)) {
            LOGGER.debug("OpenPaas admin password not specified.");
            return Optional.empty();
        }

        return Optional.of(openPaasAdminPassword);
    }

}
