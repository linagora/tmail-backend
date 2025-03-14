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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.ContainerState;

public class DockerOpenPaasExtensionTest {

    @RegisterExtension
    static DockerOpenPaasExtension dockerOpenPaasExtension = new DockerOpenPaasExtension(DockerOpenPaasSetup.SINGLETON);

    @Test
    void allServersShouldStartSuccessfully() {
       assertTrue(dockerOpenPaasExtension.dockerOpenPaasSetup().getAllContainers()
           .stream().allMatch(ContainerState::isRunning));
    }

    @Test
    void newTestUserShouldSucceed() {
        OpenPaasUser user = dockerOpenPaasExtension.newTestUser();
        assertAll(
            () -> assertThat("User id should not be null", user.id() != null),
            () -> assertThat("User firstname should not be null", user.firstname() != null),
            () -> assertThat("User firstname is not valid", user.firstname().matches("User_.*")),
            () -> assertThat("User lastname should not be null", user.lastname() != null),
            () -> assertThat("User lastname is not valid", user.lastname().matches("User_.*")),
            () -> assertThat("User email should not be null", user.email() != null),
            () -> assertThat("User email is not valid", user.email().matches("user_.*@open-paas.org")),
            () -> assertThat("User password should not be null", user.password() != null)
        );
    }
}
