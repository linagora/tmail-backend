package com.linagora.apisix.plugin;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.RedisException;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisStringCommands;

public class RedisRevokedTokenRepository implements IRevokedTokenRepository {

    public static final Duration CACHE_DURATION = Duration.ofDays(7);
    public static final Boolean IGNORE_REDIS_ERRORS = true;
    private final Logger logger = LoggerFactory.getLogger("RevokedTokenPlugin");

    private final RedisStringCommands<String, String> redisCommand;

    private final boolean ignoreRedisErrors;

    public RedisRevokedTokenRepository(RedisStringCommands<String, String> redisCommand,
                                       boolean ignoreRedisErrors) {
        this.redisCommand = redisCommand;
        this.ignoreRedisErrors = ignoreRedisErrors;
    }

    @Override
    public void add(String sid) {
        try {
            redisCommand.set(sid, Boolean.TRUE.toString(), SetArgs.Builder.ex(CACHE_DURATION));
        } catch (RedisException e) {
            if (!ignoreRedisErrors) {
                throw e;
            }
            logger.warn("Error while add revoked token in Redis: {}", e.getMessage());
        }
    }

    @Override
    public boolean exist(String sid) {
        try {
            return Boolean.TRUE.toString().equalsIgnoreCase(redisCommand.get(sid));
        } catch (RedisException e) {
            if (!ignoreRedisErrors) {
                throw e;
            }
            logger.warn("Error while checking token in Redis: {}", e.getMessage());
            return false;
        }
    }
}
