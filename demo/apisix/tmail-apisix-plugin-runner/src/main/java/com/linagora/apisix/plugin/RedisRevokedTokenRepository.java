package com.linagora.apisix.plugin;

import java.time.Duration;

import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisStringCommands;

public class RedisRevokedTokenRepository implements IRevokedTokenRepository {
    public static final Duration CACHE_DURATION = Duration.ofDays(7);

    private final RedisStringCommands<String, String> redisCommand;

    public RedisRevokedTokenRepository(RedisStringCommands<String, String> redisCommand) {
        this.redisCommand = redisCommand;
    }

    @Override
    public void add(String sid) {
        redisCommand.set(sid, Boolean.TRUE.toString(), SetArgs.Builder.ex(CACHE_DURATION));
    }

    @Override
    public boolean exist(String sid) {
        return Boolean.TRUE.toString().equalsIgnoreCase(redisCommand.get(sid));
    }
}
