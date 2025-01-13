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

package com.linagora.tmail.deployment;

import static com.linagora.tmail.deployment.JmxCredentialsFixture.JMX_PASSWORD;
import static com.linagora.tmail.deployment.JmxCredentialsFixture.JMX_USER;
import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;

public interface CliContract {
    GenericContainer<?> jamesContainer();

    @Test
    default void cliShouldWork() throws Exception {
        Container.ExecResult exec1 = jamesContainer().execInContainer("james-cli", "-username", JMX_USER, "-password", JMX_PASSWORD, "ListDomains");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(exec1.getExitCode()).isEqualTo(0);
            softly.assertThat(exec1.getStdout()).doesNotContain("domain.tld");
        });

        assertThat(exec1.getStdout())
            .doesNotContain("domain.tld");

        Container.ExecResult exec2 = jamesContainer().execInContainer("james-cli", "-username", JMX_USER, "-password", JMX_PASSWORD, "addDomain", "domain.tld");
        assertThat(exec2.getExitCode()).isEqualTo(0);

        Container.ExecResult exec3 = jamesContainer().execInContainer("james-cli", "-username", JMX_USER, "-password", JMX_PASSWORD, "ListDomains");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(exec3.getExitCode()).isEqualTo(0);
            softly.assertThat(exec3.getStdout()).contains("domain.tld");
        });
    }
}
