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

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;

import org.apache.http.client.utils.URIBuilder;
import org.junit.platform.commons.util.Preconditions;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.wait.strategy.Wait;

import com.github.fge.lambdas.Throwing;

public class DockerOpenPaasSetup {
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

    private final ComposeContainer environment;
    private OpenPaaSProvisioningService openPaaSProvisioningService;

    {
        try {
            environment = new ComposeContainer(
                new File(DockerOpenPaasSetup.class.getResource("/docker-openpaas-setup.yml").toURI()))
                .withExposedService(DockerService.OPENPAAS.serviceName(), DockerService.OPENPAAS.port())
                .withExposedService(DockerService.RABBITMQ.serviceName(), DockerService.RABBITMQ.port())
                .withExposedService(DockerService.RABBITMQ_ADMIN.serviceName(), DockerService.RABBITMQ_ADMIN.port())
                .withExposedService(DockerService.SABRE_DAV.serviceName(), DockerService.SABRE_DAV.port())
                .withExposedService(DockerService.MONGO.serviceName(), DockerService.MONGO.port())
                .withExposedService(DockerService.ELASTICSEARCH.serviceName(), DockerService.ELASTICSEARCH.port())
                .withExposedService(DockerService.REDIS.serviceName(), DockerService.REDIS.port())
                .waitingFor("openpaas", Wait.forLogMessage(".*Users currently connected.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(3)));
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to initialize OpenPaas Setup from docker compose.", e);
        }
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
}