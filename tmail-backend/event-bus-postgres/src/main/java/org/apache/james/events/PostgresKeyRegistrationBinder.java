package org.apache.james.events;

import static org.apache.james.events.PostgresEventBusModule.PostgresEventBusNotificationBindingsTable.CHANNEL;
import static org.apache.james.events.PostgresEventBusModule.PostgresEventBusNotificationBindingsTable.ROUTING_KEY;
import static org.apache.james.events.PostgresEventBusModule.PostgresEventBusNotificationBindingsTable.TABLE_NAME;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

class PostgresKeyRegistrationBinder {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresKeyRegistrationBinder.class);

    private final PostgresExecutor postgresExecutor;
    private final RegistrationQueueName registrationChannel;

    PostgresKeyRegistrationBinder(PostgresExecutor postgresExecutor,
                                  RegistrationQueueName registrationChannel) {
        this.postgresExecutor = postgresExecutor;
        this.registrationChannel = registrationChannel;
    }

    Mono<Void> bind(RegistrationKey key) {
        return insertBinding(RoutingKeyConverter.RoutingKey.of(key), registrationChannel.asString());
    }

    Mono<Void> unbind(RegistrationKey key) {
        return removeBinding(RoutingKeyConverter.RoutingKey.of(key), registrationChannel.asString());
    }

    private Mono<Void> insertBinding(RoutingKeyConverter.RoutingKey routingKey, String channel) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
                .set(ROUTING_KEY, routingKey.asString())
                .set(CHANNEL, channel)
                .onConflictDoNothing()))
            .doOnSuccess(any -> LOGGER.info("Registered a key-channel mapping to Postgres with key {} and channel {}", routingKey.asString(), channel))
            .then();
    }

    private Mono<Void> removeBinding(RoutingKeyConverter.RoutingKey routingKey, String channel) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
                .where(ROUTING_KEY.equal(routingKey.asString())
                    .and(CHANNEL.equal(channel)))))
            .doOnSuccess(any -> LOGGER.info("Unregistered a key-channel mapping from Postgres with key {} and channel {}", routingKey.asString(), channel))
            .then();
    }
}