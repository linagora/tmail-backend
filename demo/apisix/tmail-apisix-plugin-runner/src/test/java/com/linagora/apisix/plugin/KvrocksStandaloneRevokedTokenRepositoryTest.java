/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

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
import io.lettuce.core.api.sync.RedisStringCommands;

class KvrocksStandaloneRevokedTokenRepositoryTest implements RevokedTokenRepositoryContract {
    static final String REDIS_PASSWORD = "redisSecret1";

    static GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("apache/kvrocks:2.11.1"))
        .withCommand("--requirepass", REDIS_PASSWORD)
        .withExposedPorts(6666);
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
        RedisStringCommands<String, String> redisStringCommands = AppConfiguration.initRedisCommandStandalone(
            String.format("%s:%d", REDIS_CONTAINER.getHost(), REDIS_CONTAINER.getMappedPort(6666)),
            REDIS_PASSWORD, Duration.ofSeconds(3));
        testee = new RedisRevokedTokenRepository(redisStringCommands, IGNORE_REDIS_ERRORS);
    }

    @AfterEach
    void afterEach() throws IOException, InterruptedException {
        ContainerHelper.unPause(REDIS_CONTAINER);

        REDIS_CONTAINER.execInContainer("redis-cli", "-p", "6666", "-a", REDIS_PASSWORD, "flushall");
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
        testee = new RedisRevokedTokenRepository(AppConfiguration.initRedisCommandStandalone(
            String.format("%s:%d", REDIS_CONTAINER.getHost(), REDIS_CONTAINER.getMappedPort(6666)),
            REDIS_PASSWORD, Duration.ofSeconds(3)), ignoreRedisErrors);
        ContainerHelper.pause(REDIS_CONTAINER);
        TimeUnit.SECONDS.sleep(1);
        assertThatThrownBy(() -> testee().exist("sid1")).isInstanceOf(RedisException.class);
    }
}
