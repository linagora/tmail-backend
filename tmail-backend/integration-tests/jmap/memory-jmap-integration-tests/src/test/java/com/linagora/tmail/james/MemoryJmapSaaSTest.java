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

package com.linagora.tmail.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import java.io.File;

import org.apache.james.GuiceJamesServer;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.io.TempDir;

import com.github.fge.lambdas.Throwing;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.linagora.tmail.common.module.SaaSProbeModule;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemorySaaSModule;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.JmapSaasContract;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.calendar.ConfigurationPathFactory;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class MemoryJmapSaaSTest implements JmapSaasContract {
    @TempDir
    private File tmpDir;

    private GuiceJamesServer guiceJamesServer;

    @Override
    public GuiceJamesServer startJmapServer(boolean saasSupport) {
        guiceJamesServer = MemoryServer.createServer(MemoryConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationPath(ConfigurationPathFactory.create(tmpDir).withoutCalendarSupport())
                .usersRepository(DEFAULT)
                .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
                .build())
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(provideSaaSModule(saasSupport));

        Throwing.runnable(() -> guiceJamesServer.start()).run();
        return guiceJamesServer;
    }

    @Override
    public void stopJmapServer() {
        if (guiceJamesServer != null && guiceJamesServer.isStarted()) {
            guiceJamesServer.stop();
        }
    }

    @Override
    public void publishAmqpSettingsMessage(String message, String routingKey) {
        throw new UnsupportedOperationException("No RabbitMQ in memory app");
    }

    private Module provideSaaSModule(boolean saasSupport) {
        if (saasSupport) {
            return Modules.combine(new MemorySaaSModule(), new SaaSProbeModule());
        }
        return Modules.EMPTY_MODULE;
    }

    @Disabled("Memory app does not support RabbitMQ consumer")
    @Test
    public void planNameShouldBeSetWhenSubscriptionUpdateAndUserHasNoPlanYet() {

    }

    @Disabled("Memory app does not support RabbitMQ consumer")
    @Test
    public void planNameShouldBeUpdatedWhenSubscriptionUpdateAndUserAlreadyHasAPlan() {

    }

    @Disabled("Memory app does not support RabbitMQ consumer")
    @Test
    public void shouldNotSetPlanNameWhenSaaSModuleIsNotEnabled() {

    }

    @Disabled("Memory app does not support RabbitMQ consumer")
    @Test
    public void domainShouldBeCreatedWhenDomainSubscriptionValidated() {

    }

    @Disabled("Memory app does not support RabbitMQ consumer")
    @Test
    public void quotaForUserShouldBeDomainByDefaultWhenDefined() {

    }

    @Disabled("Memory app does not support RabbitMQ consumer")
    @Test
    public void quotaForUserShouldBeUserByDefaultWhenDomainAndUserQuotaDefined() {

    }

    @Disabled("Memory app does not support RabbitMQ consumer")
    @Test
    public void domainShouldBeRemovedWhenDomainSubscriptionDisabled() {

    }
}
