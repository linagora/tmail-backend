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

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class DockerOpenPaasExtension implements ParameterResolver, BeforeAllCallback,
    AfterAllCallback{

    private static final DockerOpenPaasSetup DOCKER_OPEN_PAAS_SINGLETON = new DockerOpenPaasSetup();
    private static final int OPEN_PASS_TEST_USERS_COUNT = 20;

    private DockerOpenPaasPopulateService dockerOpenPaasPopulateService = new DockerOpenPaasPopulateService();

    public static DockerOpenPaasSetup getDockerOpenPaasSingleton() {
        return DOCKER_OPEN_PAAS_SINGLETON;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws
        ParameterResolutionException {
        return (parameterContext.getParameter().getType() == DockerOpenPaasSetup.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return DOCKER_OPEN_PAAS_SINGLETON;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        DOCKER_OPEN_PAAS_SINGLETON.start();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        DOCKER_OPEN_PAAS_SINGLETON.stop();
    }

    public DockerOpenPaasSetup getDockerOpenPaasSetup() {
        return DOCKER_OPEN_PAAS_SINGLETON;
    }

    public OpenPaasUser newTestUser() {
        return dockerOpenPaasPopulateService.createUser()
            .map(OpenPaasUser::fromDocument)
            .block();
    }
}