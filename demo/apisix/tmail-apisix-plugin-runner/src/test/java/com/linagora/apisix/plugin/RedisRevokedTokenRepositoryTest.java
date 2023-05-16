package com.linagora.apisix.plugin;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class RedisRevokedTokenRepositoryTest implements RevokedTokenRepositoryContract {
    static final String REDIS_PASSWORD = "redisSecret1";

    static GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("bitnami/redis:7.0.4-debian-11-r25"))
        .withEnv("REDIS_PASSWORD", REDIS_PASSWORD)
        .withExposedPorts(6379);
    RedisRevokedTokenRepository testee;

    @BeforeAll
    static void setup() {
        REDIS_CONTAINER.start();
    }

    @AfterAll
    static void afterAll() {
        REDIS_CONTAINER.stop();
    }

    @BeforeEach
    void beforeEach() {
        testee = new RedisRevokedTokenRepository(AppConfiguration.initRedisCommand(String.format("localhost:%d", REDIS_CONTAINER.getMappedPort(6379)), REDIS_PASSWORD, false));
    }

    @AfterEach
    void afterEach() throws IOException, InterruptedException {
        REDIS_CONTAINER.execInContainer("redis-cli", "-a", REDIS_PASSWORD, "flushall");
        TimeUnit.MILLISECONDS.sleep(100);
    }

    @Override
    public IRevokedTokenRepository testee() {
        return testee;
    }
}
