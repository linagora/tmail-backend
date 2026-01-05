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
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.GuiceJamesServer;
import org.apache.james.server.core.configuration.Configuration.ConfigurationPath;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.github.fge.lambdas.Throwing;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.linagora.tmail.OpenPaasModuleChooserConfiguration;
import com.linagora.tmail.OpenPaasTestModule;
import com.linagora.tmail.dav.DavServerExtension;
import com.linagora.tmail.dav.WireMockOpenPaaSServerExtension;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.CalendarEventSupportCapabilityContract;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.calendar.ConfigurationPathFactory;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class MemoryCalendarEventSupportCapabilityTest implements CalendarEventSupportCapabilityContract {

    @RegisterExtension
    static WireMockOpenPaaSServerExtension openPaasServerExtension = new WireMockOpenPaaSServerExtension();
    @RegisterExtension
    static DavServerExtension davServerExtension = new DavServerExtension();

    @TempDir
    private File tmpDir;

    private GuiceJamesServer guiceJamesServer;

    @Override
    public GuiceJamesServer startJmapServer(boolean calDavSupport) {
        Pair<OpenPaasModuleChooserConfiguration, Module> openPaasModuleChooserConfigurationPair = getOpenPaasModuleChooserConfigurationModule(calDavSupport);

        guiceJamesServer = MemoryServer.createServer(MemoryConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationPath(getConfigurationPath(calDavSupport))
                .usersRepository(DEFAULT)
                .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
                .openPaasModuleChooserConfiguration(openPaasModuleChooserConfigurationPair.getKey())
                .build())
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(openPaasModuleChooserConfigurationPair.getRight());

        Throwing.runnable(() -> guiceJamesServer.start()).run();
        return guiceJamesServer;
    }

    @Override
    public void stopJmapServer() {
        if (guiceJamesServer != null && guiceJamesServer.isStarted()) {
            guiceJamesServer.stop();
        }
    }

    private static Pair<OpenPaasModuleChooserConfiguration, Module> getOpenPaasModuleChooserConfigurationModule(boolean calDavSupport) {
        if (calDavSupport) {
            return Pair.of(OpenPaasModuleChooserConfiguration.ENABLED_DAV,
                new OpenPaasTestModule(openPaasServerExtension, Optional.of(davServerExtension.getDavConfiguration()), Optional.empty()));
        }
        return Pair.of(OpenPaasModuleChooserConfiguration.DISABLED,
            Modules.EMPTY_MODULE);
    }

    private ConfigurationPath getConfigurationPath(boolean calDavSupport) {
        ConfigurationPathFactory configurationPathFactory = ConfigurationPathFactory.create(tmpDir);
        if (calDavSupport) {
            return configurationPathFactory.withCalendarSupport();
        }
        return configurationPathFactory.withoutCalendarSupport();
    }
}
