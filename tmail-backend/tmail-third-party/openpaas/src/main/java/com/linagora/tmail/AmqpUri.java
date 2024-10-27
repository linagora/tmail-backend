package com.linagora.tmail;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

public class AmqpUri {
    private static final String DEFAULT_USER = "guest";
    private static final String DEFAULT_PASSWORD_STRING = "guest";
    private static final char[] DEFAULT_PASSWORD = DEFAULT_PASSWORD_STRING.toCharArray();
    private static final RabbitMQConfiguration.ManagementCredentials DEFAULT_MANAGEMENT_CREDENTIAL = new RabbitMQConfiguration.ManagementCredentials(DEFAULT_USER, DEFAULT_PASSWORD);

    private final URI uri;
    private final RabbitMQConfiguration.ManagementCredentials managementCredentials;
    private final Optional<String> vhost;

    public AmqpUri(URI uri) {
        Preconditions.checkNotNull(uri);
        Preconditions.checkArgument("amqp".equals(uri.getScheme()) || "amqps".equals(uri.getScheme()));
        this.uri = uri;
        managementCredentials = Optional.ofNullable(uri.getUserInfo())
            .map(this::parseUserInfo)
            .orElse(DEFAULT_MANAGEMENT_CREDENTIAL);
        vhost = getVhostFromPath();
    }

    public static AmqpUri from(URI uri) {
        return new AmqpUri(uri);
    }

    public static AmqpUri from(String uri) {
        return new AmqpUri(URI.create(uri));
    }

    public Optional<AmqpUri> asOptional() {
        return Optional.of(this);
    }

    public RabbitMQConfiguration.ManagementCredentials getManagementCredentials() {
        return managementCredentials;
    }

    public Optional<String> getVhost() {
        return vhost;
    }

    public RabbitMQConfiguration toRabbitMqConfiguration() {
        return RabbitMQConfiguration.builder()
            .amqpUri(uri)
            .managementUri(uri)
            .managementCredentials(getManagementCredentials())
            .vhost(getVhost())
            .build();
    }

    private Optional<String> getVhostFromPath() {
        String vhostPath = uri.getPath();
        if (vhostPath.startsWith("/")) {
            return Optional.of(vhostPath.substring(1))
                .filter(value -> !value.isEmpty());
        }
        return Optional.empty();
    }

    // TODO: Copy pasted from AmqpForwardAttribute, find a way to remove duplication.
    private RabbitMQConfiguration.ManagementCredentials parseUserInfo(String userInfo) {
        Preconditions.checkArgument(userInfo.contains(":"), "User info needs a password part");

        List<String> parts = Splitter.on(':')
            .splitToList(userInfo);
        ImmutableList<String> passwordParts = parts.stream()
            .skip(1)
            .collect(ImmutableList.toImmutableList());

        return new RabbitMQConfiguration.ManagementCredentials(
            parts.get(0),
            Joiner.on(':')
                .join(passwordParts)
                .toCharArray());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AmqpUri amqpUri)) {
            return false;
        }
        return Objects.equals(uri, amqpUri.uri) &&
               Objects.equals(managementCredentials, amqpUri.managementCredentials) &&
               Objects.equals(vhost, amqpUri.vhost);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, managementCredentials, vhost);
    }
}
