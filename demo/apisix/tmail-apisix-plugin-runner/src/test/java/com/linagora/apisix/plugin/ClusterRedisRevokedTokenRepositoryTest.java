package com.linagora.apisix.plugin;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import io.lettuce.core.api.sync.RedisStringCommands;

public class ClusterRedisRevokedTokenRepositoryTest implements RevokedTokenRepositoryContract {

    static Network dockernetwork = Network.newNetwork();

    static GenericContainer<?> REDIS_MASTER = new GenericContainer<>(DockerImageName.parse("bitnami/redis:7.0.4-debian-11-r25"))
        .withNetworkAliases("redis-master")
        .withCommand()
        .withNetwork(dockernetwork)
        .withEnv("REDIS_REPLICATION_MODE", "master")
        .withEnv("REDIS_PASSWORD", "my_master_password")
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
        .withEnv("REDIS_MASTER_PASSWORD", "my_master_password")
        .withEnv("REDIS_PASSWORD", "my_replica_password")
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
        RedisStringCommands<String, String> stringStringRedisStringCommands = AppConfiguration.initRedisCommand(
            String.format("redis://%s@localhost:%d/0,redis://%s@localhost:%d/0",
                "my_master_password", REDIS_MASTER.getMappedPort(6379),
                "my_replica_password", REDIS_REPLICA.getMappedPort(6379)), true);

        revokedTokenRepository = new RedisRevokedTokenRepository(stringStringRedisStringCommands);
    }

    @AfterEach
    void afterEach() throws IOException, InterruptedException {
        REDIS_MASTER.execInContainer("redis-cli", "-a", "my_master_password", "flushall");
        TimeUnit.MILLISECONDS.sleep(100);
    }

    @Override
    public IRevokedTokenRepository testee() {
        return revokedTokenRepository;
    }
}
