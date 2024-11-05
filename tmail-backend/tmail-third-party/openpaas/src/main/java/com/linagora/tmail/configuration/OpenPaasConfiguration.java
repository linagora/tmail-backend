package com.linagora.tmail.configuration;

import java.net.MalformedURLException;
import java.net.URI;

import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.AmqpUri;

import spark.utils.StringUtils;

public record OpenPaasConfiguration(
    AmqpUri rabbitMqUri,
    URI apirUri,
    String adminUsername,
    String adminPassword) {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenPaasConfiguration.class);
    private static final String RABBITMQ_URI_PROPERTY = "rabbitmq.uri";
    private static final String OPENPAAS_API_URI = "openpaas.api.uri";
    private static final String OPENPAAS_ADMIN_USER_PROPERTY = "openpaas.admin.user";
    private static final String OPENPAAS_ADMIN_PASSWORD_PROPERTY = "openpaas.admin.password";

    public static OpenPaasConfiguration from(Configuration configuration) {
        AmqpUri rabbitMqUri = readRabbitMqUri(configuration);
        URI openPaasApiUri = readApiUri(configuration);
        String adminUser = readAdminUsername(configuration);
        String adminPassword = readAdminPassword(configuration);

        return new OpenPaasConfiguration(rabbitMqUri, openPaasApiUri, adminUser, adminPassword);
    }

    private static AmqpUri readRabbitMqUri(Configuration configuration) {
        String rabbitMqUri = configuration.getString(RABBITMQ_URI_PROPERTY);
        if (StringUtils.isBlank(rabbitMqUri)) {
            throw new IllegalStateException("RabbitMQ URI not defined in openpaas.properties.");
        }

        try {
            return AmqpUri.from(URI.create(rabbitMqUri));
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

}
