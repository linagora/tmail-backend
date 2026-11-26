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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail;

import static com.linagora.tmail.configuration.DavConfiguration.DEFAULT_RESPONSE_TIMEOUT;
import static com.linagora.tmail.configuration.OpenPaasConfiguration.OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.utils.URIBuilder;
import org.junit.platform.commons.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.utility.MountableFile;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.linagora.tmail.configuration.DavConfiguration;
import com.linagora.tmail.configuration.OpenPaasConfiguration;

public class DockerOpenPaasSetup {

    // Declared before SINGLETON: static fields are initialized in declaration order
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerOpenPaasSetup.class);
    // The first start of a test JVM is cold (image build, JIT): on a loaded CI agent it exceeded 3 minutes
    private static final Duration SLOW_STARTING_SERVICE_TIMEOUT = Duration.ofMinutes(5);
    private static final String COMPOSE_DIRECTORY = "docker-openpaas-setup";
    private static final String COMPOSE_FILE = "docker-compose.yml";

    public static final DockerOpenPaasSetup SINGLETON = new DockerOpenPaasSetup();

    enum DockerService {
        OPENPAAS("openpaas", 8080),
        RABBITMQ("rabbitmq", 5672),
        RABBITMQ_ADMIN("rabbitmq", 15672),
        SABRE_DAV("sabre_dav", 80),
        MONGO("mongo", 27017),
        REDIS("redis", 6379);

        private final String serviceName;
        private final Integer port;

        DockerService(String serviceName, Integer port) {
            this.serviceName = serviceName;
            this.port = port;
        }

        public String serviceName() {
            return serviceName;
        }

        public Integer port() {
            return port;
        }
    }

    public static final String DAV_ADMIN = "admin";
    public static final String DAV_ADMIN_PASSWORD = "secret123";
    public static final String OPENPAAS_ADMIN_USERNAME = "admin@open-paas.org";
    public static final String OPENPAAS_ADMIN_PASSWORD = "secret";
    private static final boolean TRUST_ALL_SSL_CERTS = true;

    private final ComposeContainer environment;
    private OpenPaaSProvisioningService openPaaSProvisioningService;
    private boolean started;
    private boolean shutdownHookRegistered;

    {
        environment = new ComposeContainer(composeFile())
            // The services carrying configuration are built from the compose directory: drop these images on stop
            .withRemoveImages(ComposeContainer.RemoveImages.LOCAL)
            .withExposedService(DockerService.OPENPAAS.serviceName(), DockerService.OPENPAAS.port(), slowStartingPortWaitStrategy())
            .withExposedService(DockerService.RABBITMQ.serviceName(), DockerService.RABBITMQ.port())
            .withExposedService(DockerService.RABBITMQ_ADMIN.serviceName(), DockerService.RABBITMQ_ADMIN.port())
            .withExposedService(DockerService.SABRE_DAV.serviceName(), DockerService.SABRE_DAV.port(), slowStartingPortWaitStrategy())
            .withExposedService(DockerService.MONGO.serviceName(), DockerService.MONGO.port())
            .withExposedService(DockerService.REDIS.serviceName(), DockerService.REDIS.port())
            .waitingFor("openpaas", Wait.forLogMessage(".*Twake Calendar side server started.*", 1)
                .withStartupTimeout(SLOW_STARTING_SERVICE_TIMEOUT))
            // Surface the side service output in the test logs: a startup timeout is otherwise impossible to diagnose
            .withLogConsumer(DockerService.OPENPAAS.serviceName(), new Slf4jLogConsumer(LOGGER).withPrefix(DockerService.OPENPAAS.serviceName()));
    }

    // The compose file is extracted along with its sibling configuration directory, used as build context: when loaded
    // from the test-jar (integration tests), the classpath directory is extracted as a whole to a temporary location.
    private static File composeFile() {
        File composeDirectory = new File(MountableFile.forClasspathResource(COMPOSE_DIRECTORY).getFilesystemPath());
        return new File(composeDirectory, COMPOSE_FILE);
    }

    // The Twake Calendar side service (a JVM) and Sabre only start once MongoDB and RabbitMQ are healthy:
    // on a loaded CI agent they can take longer than the 60 seconds of the default listening port wait strategy.
    private static WaitStrategy slowStartingPortWaitStrategy() {
        return Wait.forListeningPort()
            .withStartupTimeout(SLOW_STARTING_SERVICE_TIMEOUT);
    }

    public synchronized void start() {
        if (started) {
            return;
        }

        environment.start();
        openPaaSProvisioningService = new OpenPaaSProvisioningService(getMongoDbIpAddress().toString());
        started = true;
        registerShutdownHook();
    }

    public synchronized void stop() {
        if (!started) {
            return;
        }

        environment.stop();
        started = false;
        openPaaSProvisioningService = null;
    }

    private void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "docker-openpaas-setup-shutdown-hook"));
        shutdownHookRegistered = true;
    }

    public ContainerState getOpenPaasContainer() {
        return environment.getContainerByServiceName(DockerService.OPENPAAS.serviceName()).orElseThrow();
    }

    public URI getOpenPaasIpAddress() {
        return Throwing.supplier(() -> new URIBuilder()
            .setScheme("http")
            .setHost(getHost(DockerService.OPENPAAS))
            .setPort(getPort(DockerService.OPENPAAS))
            .build()).get();
    }

    public ContainerState getRabbitMqContainer() {
        return environment.getContainerByServiceName(DockerService.RABBITMQ.serviceName()).orElseThrow();
    }

    public URI rabbitMqUri() {
        return Throwing.supplier(() -> new URIBuilder()
            .setScheme("amqp")
            .setHost(getHost(DockerService.RABBITMQ))
            .setPort(getPort(DockerService.RABBITMQ))
            .build()).get();
    }

    public URI rabbitMqManagementUri() {
        return Throwing.supplier(() -> new URIBuilder()
            .setScheme("http")
            .setHost(getHost(DockerService.RABBITMQ_ADMIN))
            .setPort(getPort(DockerService.RABBITMQ_ADMIN))
            .build()).get();
    }

    public ContainerState getSabreDavContainer() {
        return environment.getContainerByServiceName(DockerService.SABRE_DAV.serviceName()).orElseThrow();
    }

    public URI getSabreDavURI() {
        return Throwing.supplier(() -> new URIBuilder()
            .setScheme("http")
            .setHost(getHost(DockerService.SABRE_DAV))
            .setPort(getPort(DockerService.SABRE_DAV))
            .build()).get();
    }

    public ContainerState getMongoDBContainer() {
        return environment.getContainerByServiceName(DockerService.MONGO.serviceName()).orElseThrow();
    }

    public URI getMongoDbIpAddress() {
        return Throwing.supplier(() -> new URIBuilder()
            .setScheme("mongodb")
            .setHost(getHost(DockerService.MONGO))
            .setPort(getPort(DockerService.MONGO))
            .build()).get();
    }

    public ContainerState getRedisContainer() {
        return environment.getContainerByServiceName(DockerService.REDIS.serviceName()).orElseThrow();
    }

    public List<ContainerState> getAllContainers() {
        return List.of(getOpenPaasContainer(),
            getRabbitMqContainer(),
            getSabreDavContainer(),
            getMongoDBContainer(),
            getRedisContainer());
    }

    public OpenPaaSProvisioningService getOpenPaaSProvisioningService() {
        Preconditions.notNull(openPaaSProvisioningService, "OpenPaas Provisioning Service not initialized");
        return openPaaSProvisioningService;
    }

    private String getHost(DockerService dockerService) {
        return environment.getServiceHost(dockerService.serviceName(), dockerService.port());
    }

    private Integer getPort(DockerService dockerService) {
        return environment.getServicePort(dockerService.serviceName(), dockerService.port());
    }

    public DavConfiguration davConfiguration() {
        return new DavConfiguration(
            new UsernamePasswordCredentials(DAV_ADMIN, DAV_ADMIN_PASSWORD),
            getSabreDavURI(),
            TRUST_ALL_SSL_CERTS,
            DEFAULT_RESPONSE_TIMEOUT);
    }

    public OpenPaasConfiguration openPaasConfiguration() {
        return new OpenPaasConfiguration(
            URI.create(getOpenPaasIpAddress().toString() + "/api"),
            OPENPAAS_ADMIN_USERNAME,
            OPENPAAS_ADMIN_PASSWORD,
            TRUST_ALL_SSL_CERTS,
            OpenPaasConfiguration.DEFAULT_RESPONSE_TIMEOUT,
            Optional.of(new OpenPaasConfiguration.ContactConsumerConfiguration(
                ImmutableList.of(AmqpUri.from(rabbitMqUri())),
                OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED)),
            Optional.of(davConfiguration()));
    }

}