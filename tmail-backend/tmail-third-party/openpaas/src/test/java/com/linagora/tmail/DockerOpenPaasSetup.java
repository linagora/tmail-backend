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

import static com.linagora.tmail.configuration.OpenPaasConfiguration.OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.utils.URIBuilder;
import org.junit.platform.commons.util.Preconditions;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.linagora.tmail.configuration.DavConfiguration;
import com.linagora.tmail.configuration.OpenPaasConfiguration;

public class DockerOpenPaasSetup {

    public static final DockerOpenPaasSetup SINGLETON = new DockerOpenPaasSetup();

    enum DockerService {
        OPENPAAS("openpaas", 8080),
        RABBITMQ("rabbitmq", 5672),
        RABBITMQ_ADMIN("rabbitmq", 15672),
        SABRE_DAV("sabre_dav", 80),
        MONGO("mongo", 27017),
        ELASTICSEARCH("elasticsearch", 9200),
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

    {
        MountableFile mountableFile = MountableFile.forClasspathResource("docker-openpaas-setup.yml");
        environment = new ComposeContainer(new File(mountableFile.getFilesystemPath()))
            .withExposedService(DockerService.OPENPAAS.serviceName(), DockerService.OPENPAAS.port())
            .withExposedService(DockerService.RABBITMQ.serviceName(), DockerService.RABBITMQ.port())
            .withExposedService(DockerService.RABBITMQ_ADMIN.serviceName(), DockerService.RABBITMQ_ADMIN.port())
            .withExposedService(DockerService.SABRE_DAV.serviceName(), DockerService.SABRE_DAV.port())
            .withExposedService(DockerService.MONGO.serviceName(), DockerService.MONGO.port())
            .withExposedService(DockerService.ELASTICSEARCH.serviceName(), DockerService.ELASTICSEARCH.port())
            .withExposedService(DockerService.REDIS.serviceName(), DockerService.REDIS.port())
            .waitingFor("openpaas", Wait.forLogMessage(".*Users currently connected.*", 1)
                .withStartupTimeout(Duration.ofMinutes(3)));
    }

    public void start() {
        environment.start();
        openPaaSProvisioningService = new OpenPaaSProvisioningService(getMongoDbIpAddress().toString());
    }

    public void stop() {
        environment.stop();
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

    public ContainerState getElasticsearchContainer() {
        return environment.getContainerByServiceName(DockerService.ELASTICSEARCH.serviceName()).orElseThrow();
    }

    public ContainerState getRedisContainer() {
        return environment.getContainerByServiceName(DockerService.REDIS.serviceName()).orElseThrow();
    }

    public List<ContainerState> getAllContainers() {
        return List.of(getOpenPaasContainer(),
            getRabbitMqContainer(),
            getSabreDavContainer(),
            getMongoDBContainer(),
            getElasticsearchContainer(),
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
            Optional.empty());
    }

    public OpenPaasConfiguration openPaasConfiguration() {
        return new OpenPaasConfiguration(
            URI.create(getOpenPaasIpAddress().toString() + "/api"),
            OPENPAAS_ADMIN_USERNAME,
            OPENPAAS_ADMIN_PASSWORD,
            TRUST_ALL_SSL_CERTS,
            Optional.of(new OpenPaasConfiguration.ContactConsumerConfiguration(
                ImmutableList.of(AmqpUri.from(rabbitMqUri())),
                OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED)),
            Optional.of(davConfiguration()));
    }

}