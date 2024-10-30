package com.linagora.tmail;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;

import com.google.common.base.Preconditions;

public record AmqpUserInfo(String username, String password) {
    public AmqpUserInfo {
        Preconditions.checkNotNull(username, "Amqp username is required.");
        Preconditions.checkNotNull(password, "Amqp password is required.");
    }

    public RabbitMQConfiguration.ManagementCredentials asManagementCredentials() {
        return new RabbitMQConfiguration.ManagementCredentials(username, password.toCharArray());
    }
}
