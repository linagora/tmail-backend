package com.linagora.apisix.plugin;

import static com.linagora.apisix.plugin.RedisRevokedTokenRepository.IGNORE_REDIS_ERRORS;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.api.sync.RedisStringCommands;

@Disabled("This test is disabled because it requires a Redis Sentinel cluster to be running, for manual testing only, " +
    "can run Redis Sentinel cluster by using docker-compose sample at https://github.com/apache/james-project/blob/149595da247dfb915ecb60d239edf627616916ae/server/mailet/rate-limiter-redis/docker-compose-sample/docker-compose-with-redis-sentinel.yml")
public class RedisSentinelRevokedTokenRepositoryTest implements RevokedTokenRepositoryContract {

    static final String REDIS_SENTINEL_URL = "redis-sentinel://secret1@localhost:26379,localhost:26380,localhost:26381?sentinelMasterId=mymaster";

    RedisRevokedTokenRepository revokedTokenRepository;

    RedisStringCommands<String, String> stringStringRedisStringCommands;

    @BeforeEach
    void setup() {
        stringStringRedisStringCommands = AppConfiguration.initRedisCommandSentinel(
            REDIS_SENTINEL_URL, Duration.ofSeconds(3));
        revokedTokenRepository = new RedisRevokedTokenRepository(stringStringRedisStringCommands, IGNORE_REDIS_ERRORS);
    }

    @AfterEach
    void afterEach() {
        RedisClient redisClient = RedisClient.create(REDIS_SENTINEL_URL);
        StatefulRedisConnection<String, String> connection = redisClient.connect();
        RedisCommands<String, String> syncCommands = connection.sync();
        syncCommands.flushall();
    }

    @Override
    public IRevokedTokenRepository testee() {
        return revokedTokenRepository;
    }
}
