package com.linagora.apisix.plugin;

import static com.linagora.apisix.plugin.RedisRevokedTokenRepository.IGNORE_REDIS_ERRORS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import io.lettuce.core.RedisException;
import io.lettuce.core.api.sync.RedisStringCommands;

class RedisMasterReplicaRevokedTokenRepositoryTest implements RevokedTokenRepositoryContract {
    private static final String REDIS_PASSWORD = "my_password";

    static Network dockernetwork = Network.newNetwork();

    static GenericContainer<?> REDIS_MASTER = new GenericContainer<>(DockerImageName.parse("bitnami/redis:7.0.4-debian-11-r25"))
        .withNetworkAliases("redis-master")
        .withCommand()
        .withNetwork(dockernetwork)
        .withEnv("REDIS_REPLICATION_MODE", "master")
        .withEnv("REDIS_PASSWORD", REDIS_PASSWORD)
        .withExposedPorts(6379)
        .withClasspathResourceMapping("redis-overrides.conf",
            "/opt/bitnami/redis/mounted-etc/overrides.conf",
            BindMode.READ_ONLY)
        .withClasspathResourceMapping("fix_partition.sh",
            "/opt/bitnami/redis/fix_partition.sh",
            BindMode.READ_WRITE)
        .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("redis-master-test" + UUID.randomUUID()));

    static GenericContainer<?> REDIS_REPLICA = new GenericContainer<>(DockerImageName.parse("bitnami/redis:7.0.4-debian-11-r25"))
        .withNetwork(dockernetwork)
        .withNetworkAliases("redis-slave")
        .withEnv("REDIS_REPLICATION_MODE", "slave")
        .withEnv("REDIS_MASTER_HOST", "redis-master")
        .withEnv("REDIS_MASTER_PORT_NUMBER", "6379")
        .withEnv("REDIS_MASTER_PASSWORD", REDIS_PASSWORD)
        .withEnv("REDIS_PASSWORD", REDIS_PASSWORD)
        .withExposedPorts(6379)
        .dependsOn(REDIS_MASTER)
        .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("redis-replica-test" + UUID.randomUUID()));

    @BeforeAll
    static void beforeAll() throws InterruptedException, IOException {
        REDIS_MASTER.start();
        REDIS_REPLICA.start();

        // https://www.cxyzjd.com/article/gao_grace/95085609
        REDIS_MASTER.execInContainer("bash","/opt/bitnami/redis/fix_partition.sh");
        TimeUnit.SECONDS.sleep(2);
    }

    @AfterAll
    static void afterAll() {
        REDIS_MASTER.stop();
        REDIS_REPLICA.stop();
    }

    RedisRevokedTokenRepository revokedTokenRepository;

    @BeforeEach
    void setup() {
        RedisStringCommands<String, String> stringStringRedisStringCommands = AppConfiguration.initRedisCommandCluster(
            String.format("localhost:%d,localhost:%d",
                REDIS_MASTER.getMappedPort(6379), REDIS_REPLICA.getMappedPort(6379)),
            REDIS_PASSWORD,  Duration.ofSeconds(3));

        revokedTokenRepository = new RedisRevokedTokenRepository(stringStringRedisStringCommands, IGNORE_REDIS_ERRORS);
    }

    @AfterEach
    void afterEach() throws IOException, InterruptedException {
        ContainerHelper.unPause(REDIS_MASTER);
        REDIS_MASTER.execInContainer("redis-cli", "-a", REDIS_PASSWORD, "flushall");
        TimeUnit.MILLISECONDS.sleep(100);
    }

    @Override
    public IRevokedTokenRepository testee() {
        return revokedTokenRepository;
    }

    @Test
    void existShouldNotThrowWhenIgnoreWasConfiguredAndRedisError() throws InterruptedException {
        ContainerHelper.pause(REDIS_MASTER);
        TimeUnit.SECONDS.sleep(1);

        assertThatCode(() -> testee().exist("sid1")).doesNotThrowAnyException();
    }


    @Test
    void existsShouldReturnCorrectWhenIgnoreWasConfigured() throws InterruptedException {
        testee().add("sid1");
        assertThat(testee().exist("sid1")).isTrue();

        ContainerHelper.pause(REDIS_MASTER);
        TimeUnit.SECONDS.sleep(1);
        assertThat(testee().exist("sid1")).isFalse();

        ContainerHelper.unPause(REDIS_MASTER);
        TimeUnit.SECONDS.sleep(1);
        assertThat(testee().exist("sid1")).isTrue();
    }

    @Test
    void existsShouldThrowWhenIgnoreWasNotConfiguredAndRedisError() throws InterruptedException {
        boolean ignoreRedisErrors = false;
        RedisRevokedTokenRepository testee = new RedisRevokedTokenRepository(AppConfiguration.initRedisCommandCluster(
            String.format("localhost:%d,localhost:%d",
                REDIS_MASTER.getMappedPort(6379), REDIS_REPLICA.getMappedPort(6379)),
            REDIS_PASSWORD,  Duration.ofSeconds(3)), ignoreRedisErrors);

        ContainerHelper.pause(REDIS_MASTER);
        TimeUnit.SECONDS.sleep(1);
        assertThatThrownBy(() -> testee.exist("sid1")).isInstanceOf(RedisException.class);
    }
}
