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

import org.apache.james.GuiceJamesServer;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.OpenPaasModuleChooserConfiguration;
import com.linagora.tmail.OpenPaasTestModule;
import com.linagora.tmail.dav.DavServerExtension;
import com.linagora.tmail.dav.WireMockOpenPaaSServerExtension;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.calendar.ConfigurationPathFactory;
import com.linagora.tmail.james.common.CalendarEventSupportCapabilityContract;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
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
    public GuiceJamesServer startJmapServer() {
        guiceJamesServer = MemoryServer.createServer(MemoryConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationPath(ConfigurationPathFactory.create(tmpDir).withoutCalendarSupport())
                .usersRepository(DEFAULT)
                .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
                .openPaasModuleChooserConfiguration(OpenPaasModuleChooserConfiguration.DISABLED)
                .build())
            .overrideWith(new LinagoraTestJMAPServerModule());

        Throwing.runnable(() -> guiceJamesServer.start()).run();
        return guiceJamesServer;
    }

    @Override
    public GuiceJamesServer startJmapServerWithCalendarSupport() {
        guiceJamesServer = MemoryServer.createServer(MemoryConfiguration.builder()
                        .workingDirectory(tmpDir)
                        .configurationPath(ConfigurationPathFactory.create(tmpDir).withCalendarSupport())
                        .usersRepository(DEFAULT)
                        .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
                        .openPaasModuleChooserConfiguration(OpenPaasModuleChooserConfiguration.ENABLED_DAV)
                        .build())
                .overrideWith(new LinagoraTestJMAPServerModule())
                .overrideWith(new OpenPaasTestModule(openPaasServerExtension, Optional.of(davServerExtension.getDavConfiguration()), Optional.empty()));

        Throwing.runnable(() -> guiceJamesServer.start()).run();
        return guiceJamesServer;
    }

    @Override
    public void stopJmapServer() {
        if (guiceJamesServer != null && guiceJamesServer.isStarted()) {
            guiceJamesServer.stop();
        }
    }
}
