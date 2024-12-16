package com.linagora.apisix.plugin;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.SetArgs;
import io.lettuce.core.api.reactive.RedisStringReactiveCommands;
import reactor.core.publisher.Mono;

public class RedisRevokedTokenRepository implements IRevokedTokenRepository {

    public static final Duration CACHE_DURATION = Duration.ofDays(7);
    public static final Boolean IGNORE_REDIS_ERRORS = true;
    private final Logger logger = LoggerFactory.getLogger("RevokedTokenPlugin");

    private final RedisStringReactiveCommands<String, String> redisCommand;

    private final boolean ignoreRedisErrors;

    public RedisRevokedTokenRepository(RedisStringReactiveCommands<String, String> redisCommand,
                                       boolean ignoreRedisErrors) {
        this.redisCommand = redisCommand;
        this.ignoreRedisErrors = ignoreRedisErrors;
    }

    @Override
    public Mono<Void> add(String sid) {
        return redisCommand.set(sid, Boolean.TRUE.toString(), SetArgs.Builder.ex(CACHE_DURATION))
            .then()
            .onErrorResume(e -> {
                if (!ignoreRedisErrors) {
                    return Mono.error(e);
                }
                logger.warn("Error while add revoked token in Redis: {}", e.getMessage());
                return Mono.empty();
            });
    }

    @Override
    public Mono<Boolean> exist(String sid) {
        return redisCommand.get(sid)
            .map(Boolean.TRUE.toString()::equalsIgnoreCase)
            .switchIfEmpty(Mono.just(false))
            .onErrorResume(e -> {
                if (!ignoreRedisErrors) {
                    return Mono.error(e);
                }
                logger.warn("Error while checking token in Redis: {}", e.getMessage());
                return Mono.just(false);
            });
    }
}
