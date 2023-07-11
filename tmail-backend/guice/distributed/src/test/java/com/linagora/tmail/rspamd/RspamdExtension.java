package com.linagora.tmail.rspamd;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.util.Runnables;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import com.github.fge.lambdas.Throwing;


public class RspamdExtension implements GuiceModuleTestExtension {
    public static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);
    public static final String RSPAMD_PASSWORD = "admin";

    private static final DockerImageName RSPAMD_IMAGE = DockerImageName.parse("a16bitsysop/rspamd").withTag("3.3-r0-alpine3.16.2-r0");
    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis").withTag("6.2.6");
    private static final DockerImageName CLAMAV_IMAGE = DockerImageName.parse("clamav/clamav").withTag("0.105");
    private static final int RSPAMD_DEFAULT_PORT = 11334;
    private static final int REDIS_DEFAULT_PORT = 6379;
    private static final int CLAMAV_DEFAULT_PORT = 3310;

    private final GenericContainer<?> rspamdContainer;
    private final GenericContainer<?> redisContainer;
    private final GenericContainer<?> clamAVContainer;
    private final Network network;

    public RspamdExtension() {
        this.network = Network.newNetwork();
        this.redisContainer = redisContainer(network);
        this.clamAVContainer = clamAVContainer(network);
        this.rspamdContainer = rspamdContainer();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        if (!rspamdContainer.isRunning()) {
            rspamdContainer.start();
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        rspamdContainer.stop();
        Runnables.runParallel(redisContainer::stop, clamAVContainer::stop);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        redisFlushAll();
    }

    private GenericContainer<?> rspamdContainer() {
        return new GenericContainer<>(RSPAMD_IMAGE)
                .withExposedPorts(RSPAMD_DEFAULT_PORT)
                .withEnv("REDIS", "redis")
                .withEnv("CLAMAV", "clamav")
                .withEnv("PASSWORD", RSPAMD_PASSWORD)
                .withCopyFileToContainer(MountableFile.forClasspathResource("rspamd-config/antivirus.conf"), "/etc/rspamd/override.d/")
                .withCopyFileToContainer(MountableFile.forClasspathResource("rspamd-config/actions.conf"), "/etc/rspamd/")
                .withCopyFileToContainer(MountableFile.forClasspathResource("rspamd-config/statistic.conf"), "/etc/rspamd/")
                .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-rspamd-test-" + UUID.randomUUID()))
                .withNetwork(network)
                .dependsOn(redisContainer, clamAVContainer)
                .waitingFor(Wait.forHealthcheck())
                .withStartupTimeout(STARTUP_TIMEOUT);
    }


    private GenericContainer<?> redisContainer(Network network) {
        return new GenericContainer<>(REDIS_IMAGE)
                .withExposedPorts(REDIS_DEFAULT_PORT)
                .withNetwork(network)
                .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-redis-test"))
                .withNetworkAliases("redis");
    }

    private GenericContainer<?> clamAVContainer(Network network) {
        return new GenericContainer<>(CLAMAV_IMAGE)
                .withExposedPorts(CLAMAV_DEFAULT_PORT)
                .withEnv("CLAMAV_NO_FRESHCLAMD", "true")
                .withEnv("CLAMAV_NO_MILTERD", "true")
                .withNetwork(network)
                .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-clamav-test-" + UUID.randomUUID()))
                .withNetworkAliases("clamav")
                .waitingFor(Wait.forHealthcheck())
                .withStartupTimeout(STARTUP_TIMEOUT);
    }


    private void redisFlushAll() {
        try {
            redisContainer.execInContainer("redis-cli", "flushall");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public URL rspamdURL() {
        return Throwing.supplier(() -> new URL("http",
                rspamdContainer.getHost(),
                rspamdContainer.getMappedPort(RSPAMD_DEFAULT_PORT),
                "/")).get();
    }

}
