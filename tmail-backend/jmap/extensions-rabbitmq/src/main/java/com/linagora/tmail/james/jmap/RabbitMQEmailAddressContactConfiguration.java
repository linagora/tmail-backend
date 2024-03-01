package com.linagora.tmail.james.jmap;

import java.net.URI;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

public record RabbitMQEmailAddressContactConfiguration(String queueName, URI amqpUri,
                                                       RabbitMQConfiguration.ManagementCredentials managementCredentials,
                                                       Optional<String> vhost,
                                                       boolean useQuorumQueues,
                                                       int quorumQueueReplicationFactor) {
    static final String DEAD_LETTER_EXCHANGE_PREFIX = "TmailQueue-dead-letter-exchange-";
    static final String DEAD_LETTER_QUEUE_PREFIX = "TmailQueue-dead-letter-queue-";
    static final String AQMP_URI_PROPERTY = "address.contact.uri";
    static final String AQMP_MANAGEMENT_USER = "address.contact.user";
    static final String AQMP_MANAGEMENT_PASSWORD = "address.contact.password";
    static final String AQMP_QUEUE_NAME_PROPERTY = "address.contact.queue";
    static final String AQMP_EXCHANGE_PREFIX = "TmailExchange-";
    static final String VHOST = "vhost";
    static final String USE_QUORUM_QUEUES = "quorum.queues.enable";
    static final String QUORUM_QUEUES_REPLICATION_FACTOR = "quorum.queues.replication.factor";

    public static RabbitMQEmailAddressContactConfiguration from(Configuration configuration) {
        String aqmpURIAsString = configuration.getString(AQMP_URI_PROPERTY);
        Preconditions.checkState(!Strings.isNullOrEmpty(aqmpURIAsString),
            String.format("You need to specify the URI of RabbitMQ by '%s' property", AQMP_URI_PROPERTY));
        URI aqmpUri = URI.create(aqmpURIAsString);

        Preconditions.checkState(CharMatcher.is('/').countIn(aqmpUri.getPath()) <= 1,
            String.format("RabbitMQ URI Specification invalid '%s'", aqmpUri.toASCIIString()));

        String managementUser = configuration.getString(AQMP_MANAGEMENT_USER);
        Preconditions.checkState(!Strings.isNullOrEmpty(managementUser),
            String.format("You need to specify the %s property as username of rabbitmq account", AQMP_MANAGEMENT_USER));
        String managementPassword = configuration.getString(AQMP_MANAGEMENT_PASSWORD);
        Preconditions.checkState(!Strings.isNullOrEmpty(managementPassword),
            String.format("You need to specify the %s property as password of rabbitmq account", AQMP_MANAGEMENT_PASSWORD));
        RabbitMQConfiguration.ManagementCredentials managementCredential = new RabbitMQConfiguration.ManagementCredentials(managementUser, managementPassword.toCharArray());

        String queueName = configuration.getString(AQMP_QUEUE_NAME_PROPERTY);
        Preconditions.checkState(!Strings.isNullOrEmpty(queueName),
            String.format("You need to specify the %s property as queue name for contact address feature", AQMP_QUEUE_NAME_PROPERTY));

        Optional<String> vhost = Optional.ofNullable(configuration.getString(VHOST, null));

        boolean useQuorumQueues = configuration.getBoolean(USE_QUORUM_QUEUES, null);
        Optional<Integer> quorumQueueReplicationFactor = Optional.ofNullable(configuration.getInteger(QUORUM_QUEUES_REPLICATION_FACTOR, null));
        return new RabbitMQEmailAddressContactConfiguration(queueName, aqmpUri, managementCredential, vhost, useQuorumQueues, quorumQueueReplicationFactor.orElse(0));
    }

    public String getExchangeName() {
        return AQMP_EXCHANGE_PREFIX + queueName;
    }

    public String getDeadLetterExchange() {
        return DEAD_LETTER_EXCHANGE_PREFIX + queueName;
    }

    public String getDeadLetterQueue() {
        return DEAD_LETTER_QUEUE_PREFIX + queueName;
    }

    public Optional<String> vhost() {
        String vhostPath = amqpUri.getPath();
        if (vhostPath.startsWith("/")) {
            return Optional.of(vhostPath.substring(1))
                .filter(value -> !value.isEmpty());
        }
        return Optional.empty();
    }

    public ImmutableMap.Builder<String, Object> queueArgumentsBuilder() {
        ImmutableMap.Builder<String, Object> arguments = ImmutableMap.<String, Object>builder();

        if (useQuorumQueues) {
            arguments.put("x-queue-type", "quorum")
                .put("x-quorum-initial-group-size", quorumQueueReplicationFactor);

        }
        return arguments;
    }

    public ImmutableMap.Builder<String, Object> queueArgumentsBuilderTmp() {
        ImmutableMap.Builder<String, Object> arguments = ImmutableMap.<String, Object>builder();
        arguments.put("x-queue-type", "quorum")
            .put("x-quorum-initial-group-size", quorumQueueReplicationFactor);
        return arguments;
    }
}
