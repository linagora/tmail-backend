package org.apache.james.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.api.reactive.RedisSetReactiveCommands;
import reactor.core.publisher.Mono;

class RedisKeyRegistrationBinder {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisKeyRegistrationBinder.class);

    private final RedisSetReactiveCommands<String, String> redisSetReactiveCommands;
    private final RegistrationQueueName registrationChannel;

    RedisKeyRegistrationBinder(RedisSetReactiveCommands<String, String> redisSetReactiveCommands,
                               RegistrationQueueName registrationChannel) {
        this.redisSetReactiveCommands = redisSetReactiveCommands;
        this.registrationChannel = registrationChannel;
    }

    Mono<Void> bind(RegistrationKey key) {
        // Use Redis Set to store 1 registrationKey -> n channel(s) mapping in Redis
        RoutingKeyConverter.RoutingKey routingKey = RoutingKeyConverter.RoutingKey.of(key);
        return redisSetReactiveCommands.sadd(routingKey.asString(), registrationChannel.asString())
            // the following log should be removed once we finished monitoring Redis event bus keys implementation
            .doOnSuccess(l -> LOGGER.info("Registered {} key-channel mapping to Redis with key {} and channel {}", l, routingKey.asString(), registrationChannel.asString()))
            .then();
    }

    Mono<Void> unbind(RegistrationKey key) {
        // delete the registrationKey -> channel mapping in Redis
        RoutingKeyConverter.RoutingKey routingKey = RoutingKeyConverter.RoutingKey.of(key);
        return redisSetReactiveCommands.srem(routingKey.asString(), registrationChannel.asString())
            // the following log should be removed once we finished monitoring Redis event bus keys implementation
            .doOnSuccess(l -> LOGGER.info("Unregistered {} key-channel mapping to Redis with key {} and channel {}", l, routingKey.asString(), registrationChannel.asString()))
            .then();
    }
}