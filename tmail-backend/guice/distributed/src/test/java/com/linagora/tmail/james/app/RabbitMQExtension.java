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

package com.linagora.tmail.james.app;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.backends.rabbitmq.DockerRabbitMQ;
import org.apache.james.backends.rabbitmq.DockerRabbitMQSingleton;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Module;

public class RabbitMQExtension implements GuiceModuleTestExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQExtension.class);

    private final DockerRabbitMQ dockerRabbitMQ;

    public RabbitMQExtension() {
        this(DockerRabbitMQSingleton.SINGLETON);
    }

    public RabbitMQExtension(DockerRabbitMQ dockerRabbitMQ) {
        this.dockerRabbitMQ = dockerRabbitMQ;
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        // Container is a JVM-wide singleton started in DockerRabbitMQSingleton's
        // static initializer; testcontainers Ryuk reaps it on JVM exit.
        // Reset the RabbitMQ node between test classes to clear stale queues
        // (e.g. PushListenerGroup) left by the previous GuiceJamesServer.
        try {
            dockerRabbitMQ.reset();
        } catch (Exception e) {
            LOGGER.warn("Failed to reset RabbitMQ between test classes", e);
        }
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        dockerRabbitMQ.start();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == DockerRabbitMQ.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return dockerRabbitMQ;
    }

    @Override
    public Module getModule() {
        return new TestRabbitMQModule(dockerRabbitMQ);
    }

    public DockerRabbitMQ dockerRabbitMQ() {
        return dockerRabbitMQ;
    }

}
