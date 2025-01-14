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

import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.ExternalJamesModule;
import org.apache.james.mpt.imapmailbox.external.james.host.SmtpHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.apache.james.mpt.imapmailbox.external.james.host.external.NoopDomainsAndUserAdder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;

import com.google.inject.Guice;
import com.google.inject.Injector;

public abstract class ImapAndSmtpContract extends TMailDeploymentValidation {
    protected abstract ExternalJamesConfiguration configuration();

    protected abstract GenericContainer<?> container();

    private ImapHostSystem system;
    private SmtpHostSystem smtpHostSystem;
    private ExternalJamesConfiguration configuration;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        container().execInContainer("james-cli", "-username", JMX_USER, "-password", JMX_PASSWORD, "AddDomain", "domain");
        container().execInContainer("james-cli", "-username", JMX_USER, "-password", JMX_PASSWORD, "AddUser", "imapuser@domain", "password");

        configuration = configuration();
        Injector injector = Guice.createInjector(new ExternalJamesModule(configuration, new NoopDomainsAndUserAdder()));
        system = injector.getInstance(ImapHostSystem.class);
        smtpHostSystem = injector.getInstance(SmtpHostSystem.class);
        system.beforeTest();
        super.setUp();
    }

    @Override
    protected ImapHostSystem createImapHostSystem() {
        return system;
    }

    @Override
    protected SmtpHostSystem createSmtpHostSystem() {
        return smtpHostSystem;
    }

    @Override
    protected ExternalJamesConfiguration getConfiguration() {
        return configuration;
    }

    @AfterEach
    public void tearDown() throws Exception {
        system.afterTest();
    }
}
