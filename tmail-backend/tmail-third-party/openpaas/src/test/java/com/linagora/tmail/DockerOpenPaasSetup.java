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
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;

import org.junit.platform.commons.util.Preconditions;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.wait.strategy.Wait;

public class DockerOpenPaasSetup {
    private final ComposeContainer environment;
    private OpenPaaSProvisioningService openPaaSProvisioningService;

    {
        try {
            environment = new ComposeContainer(
                new File(DockerOpenPaasSetup.class.getResource("/docker-openpaas-setup.yml").toURI()))
                .waitingFor("openpaas", Wait.forLogMessage(".*Users currently connected.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(3)));
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to initialize OpenPaas Setup from docker compose.", e);
        }
    }

    public void start() {
        environment.start();
        openPaaSProvisioningService = new OpenPaaSProvisioningService(
            "mongodb://%s:27017".formatted(TestContainersUtils.getContainerPrivateIpAddress(getMongoDBContainer())));
    }

    public void stop() {
        environment.stop();
    }

    public ContainerState getOpenPaasContainer() {
        return environment.getContainerByServiceName("openpaas").orElseThrow();
    }

    public String getOpenPaasIpAddress() {
        return TestContainersUtils.getContainerPrivateIpAddress(getOpenPaasContainer());
    }

    public ContainerState getRabbitMqContainer() {
        return environment.getContainerByServiceName("rabbitmq").orElseThrow();
    }

    public String getRabbitMqIpAddress() {
        return TestContainersUtils.getContainerPrivateIpAddress(getRabbitMqContainer());
    }

    public ContainerState getSabreDavContainer() {
        return environment.getContainerByServiceName("sabre_dav").orElseThrow();
    }

    public String getSabreDavIpAddress() {
        return TestContainersUtils.getContainerPrivateIpAddress(getSabreDavContainer());
    }

    public ContainerState getMongoDBContainer() {
        return environment.getContainerByServiceName("mongo").orElseThrow();
    }

    public ContainerState getElasticsearchContainer() {
        return environment.getContainerByServiceName("elasticsearch").orElseThrow();
    }

    public ContainerState getRedisContainer() {
        return environment.getContainerByServiceName("redis").orElseThrow();
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
}