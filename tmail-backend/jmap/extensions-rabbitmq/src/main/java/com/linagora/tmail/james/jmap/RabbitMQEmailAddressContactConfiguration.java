package com.linagora.tmail.james.jmap;

import java.net.URI;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class RabbitMQEmailAddressContactConfiguration {
    static final String DEAD_LETTER_EXCHANGE_PREFIX = "TmailQueue-dead-letter-exchange-";
    static final String DEAD_LETTER_QUEUE_PREFIX = "TmailQueue-dead-letter-queue-";
    static final String AQMP_URI_PROPERTY = "address.contact.uri";
    static final String AQMP_MANAGEMENT_URI_PROPERTY = "address.contact.management.uri";
    static final String AQMP_MANAGEMENT_USER = "address.contact.management.user";
    static final String AQMP_MANAGEMENT_PASSWORD = "address.contact.management.password";
    static final String AQMP_EXCHANGE_NAME_PROPERTY = "address.contact.exchange";
    static final String AQMP_QUEUE_NAME_PROPERTY = "address.contact.queue";

    public static RabbitMQEmailAddressContactConfiguration from(Configuration configuration) {
        String aqmpURIAsString = configuration.getString(AQMP_URI_PROPERTY);
        Preconditions.checkState(!Strings.isNullOrEmpty(aqmpURIAsString),
            String.format("You need to specify the URI of RabbitMQ by '%s' property", AQMP_URI_PROPERTY));
        URI aqmpUri = URI.create(aqmpURIAsString);

        String managementUriAsString = configuration.getString(AQMP_MANAGEMENT_URI_PROPERTY);
        Preconditions.checkState(!Strings.isNullOrEmpty(managementUriAsString),
            String.format("You need to specify the management URI of RabbitMQ by '%s' property", AQMP_MANAGEMENT_URI_PROPERTY));
        URI managementUri = URI.create(managementUriAsString);

        String managementUser = configuration.getString(AQMP_MANAGEMENT_USER);
        Preconditions.checkState(!Strings.isNullOrEmpty(managementUser),
            String.format("You need to specify the %s property as username of rabbitmq management admin account", AQMP_MANAGEMENT_USER));
        String managementPassword = configuration.getString(AQMP_MANAGEMENT_PASSWORD);
        Preconditions.checkState(!Strings.isNullOrEmpty(managementPassword),
            String.format("You need to specify the %s property as password of rabbitmq management admin account", AQMP_MANAGEMENT_PASSWORD));
        RabbitMQConfiguration.ManagementCredentials managementCredential = new RabbitMQConfiguration.ManagementCredentials(managementUser, managementPassword.toCharArray());

        String exchangeName = configuration.getString(AQMP_EXCHANGE_NAME_PROPERTY);
        Preconditions.checkState(!Strings.isNullOrEmpty(managementUser),
            String.format("You need to specify the %s property as exchange name for contact address feature", AQMP_EXCHANGE_NAME_PROPERTY));
        String queueName = configuration.getString(AQMP_QUEUE_NAME_PROPERTY);
        Preconditions.checkState(!Strings.isNullOrEmpty(managementUser),
            String.format("You need to specify the %s property as queue name for contact address feature", AQMP_QUEUE_NAME_PROPERTY));
        return new RabbitMQEmailAddressContactConfiguration(exchangeName, queueName, aqmpUri, managementUri, managementCredential);
    }

    private final String exchangeName;
    private final String queueName;
    private final URI amqpUri;
    private final URI managementUri;
    private final RabbitMQConfiguration.ManagementCredentials managementCredentials;

    public RabbitMQEmailAddressContactConfiguration(String exchangeName,
                                                    String queueName,
                                                    URI amqpUri,
                                                    URI managementUri,
                                                    RabbitMQConfiguration.ManagementCredentials managementCredentials) {
        this.exchangeName = exchangeName;
        this.queueName = queueName;
        this.amqpUri = amqpUri;
        this.managementUri = managementUri;
        this.managementCredentials = managementCredentials;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public String getQueueName() {
        return queueName;
    }

    public String getDeadLetterExchange() {
        return DEAD_LETTER_EXCHANGE_PREFIX + getQueueName();
    }

    public String getDeadLetterQueue() {
        return DEAD_LETTER_QUEUE_PREFIX + getQueueName();
    }

    public URI getAmqpUri() {
        return amqpUri;
    }

    public URI getManagementUri() {
        return managementUri;
    }

    public RabbitMQConfiguration.ManagementCredentials getManagementCredentials() {
        return managementCredentials;
    }
}
