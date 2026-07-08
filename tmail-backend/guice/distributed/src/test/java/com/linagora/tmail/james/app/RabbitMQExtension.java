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
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

import com.google.inject.Module;

public class RabbitMQExtension implements GuiceModuleTestExtension {

    private static final DockerRabbitMQ dockerRabbitMQ = DockerRabbitMQ.withoutCookie();
    private static boolean started = false;

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        // ponytail: container persists across test classes in the same fork.
        // Testcontainers Ryuk cleans up on JVM exit.
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        if (!started) {
            dockerRabbitMQ.start();
            started = true;
        }
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
