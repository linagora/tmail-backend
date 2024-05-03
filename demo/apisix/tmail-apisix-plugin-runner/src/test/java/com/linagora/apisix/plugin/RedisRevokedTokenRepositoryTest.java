package com.linagora.apisix.plugin;

import static com.linagora.apisix.plugin.RedisRevokedTokenRepository.IGNORE_REDIS_ERRORS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import io.lettuce.core.RedisException;

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
        testee = new RedisRevokedTokenRepository(AppConfiguration.initRedisCommand(String.format("localhost:%d", REDIS_CONTAINER.getMappedPort(6379)),
            REDIS_PASSWORD, false, Duration.ofSeconds(3)), IGNORE_REDIS_ERRORS);
    }

    @AfterEach
    void afterEach() throws IOException, InterruptedException {
        ContainerHelper.unPause(REDIS_CONTAINER);

        REDIS_CONTAINER.execInContainer("redis-cli", "-a", REDIS_PASSWORD, "flushall");
        TimeUnit.MILLISECONDS.sleep(100);
    }

    @Override
    public IRevokedTokenRepository testee() {
        return testee;
    }


    @Test
    void existShouldNotThrowWhenIgnoreWasConfiguredAndRedisError() throws InterruptedException {
        ContainerHelper.pause(REDIS_CONTAINER);
        TimeUnit.SECONDS.sleep(1);

        assertThatCode(() -> testee().exist("sid1")).doesNotThrowAnyException();
    }


    @Test
    void existsShouldReturnCorrectWhenIgnoreWasConfigured() throws InterruptedException {
        testee().add("sid1");
        assertThat(testee().exist("sid1")).isTrue();

        ContainerHelper.pause(REDIS_CONTAINER);
        TimeUnit.SECONDS.sleep(1);
        assertThat(testee().exist("sid1")).isFalse();

        ContainerHelper.unPause(REDIS_CONTAINER);
        TimeUnit.SECONDS.sleep(1);
        assertThat(testee().exist("sid1")).isTrue();
    }

    @Test
    void existsShouldThrowWhenIgnoreWasNotConfiguredAndRedisError() throws InterruptedException {
        boolean ignoreRedisErrors = false;
        testee = new RedisRevokedTokenRepository(AppConfiguration.initRedisCommand(String.format("localhost:%d", REDIS_CONTAINER.getMappedPort(6379)),
            REDIS_PASSWORD, false, Duration.ofSeconds(3)), ignoreRedisErrors);
        ContainerHelper.pause(REDIS_CONTAINER);
        TimeUnit.SECONDS.sleep(1);
        assertThatThrownBy(() -> testee().exist("sid1")).isInstanceOf(RedisException.class);
    }
}
