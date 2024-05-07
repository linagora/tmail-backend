package org.apache.james.events;

import java.time.Duration;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.util.DurationParser;

public record RedisEventBusConfiguration(boolean failureIgnore, Duration durationTimeout) {
    public static final boolean FAILURE_IGNORE_DEFAULT = false;
    public static final Duration DURATION_TIMEOUT_DEFAULT = Duration.ofSeconds(10);
    public static final RedisEventBusConfiguration DEFAULT = new RedisEventBusConfiguration(FAILURE_IGNORE_DEFAULT, DURATION_TIMEOUT_DEFAULT);

    public static RedisEventBusConfiguration from(Configuration configuration) {
        return new RedisEventBusConfiguration(
            configuration.getBoolean("eventBus.redis.failure.ignore", FAILURE_IGNORE_DEFAULT),
            Optional.ofNullable(configuration.getString("eventBus.redis.timeout"))
                .map(DurationParser::parse)
                .orElse(DURATION_TIMEOUT_DEFAULT));
    }
}
