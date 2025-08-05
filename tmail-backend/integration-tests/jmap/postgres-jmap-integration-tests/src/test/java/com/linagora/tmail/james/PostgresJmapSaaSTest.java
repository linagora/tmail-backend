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

import static org.apache.james.PostgresJamesConfiguration.EventBusImpl.IN_MEMORY;

import java.io.File;

import org.apache.james.CleanupTasksPerformer;
import org.apache.james.GuiceJamesServer;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.github.fge.lambdas.Throwing;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.common.module.SaaSProbeModule;
import com.linagora.tmail.james.app.PostgresSaaSModule;
import com.linagora.tmail.james.app.PostgresTmailConfiguration;
import com.linagora.tmail.james.app.PostgresTmailServer;
import com.linagora.tmail.james.common.JmapSaasContract;
import com.linagora.tmail.james.common.probe.JmapSettingsProbeModule;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class PostgresJmapSaaSTest implements JmapSaasContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.empty();

    @TempDir
    private File tmpDir;

    private GuiceJamesServer guiceJamesServer;

    @Override
    public GuiceJamesServer startJmapServer(boolean saasSupport) {
        guiceJamesServer = PostgresTmailServer.createServer(PostgresTmailConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .blobStore(BlobStoreConfiguration.builder()
                    .postgres()
                    .disableCache()
                    .deduplication()
                    .noCryptoConfig()
                    .disableSingleSave())
                .searchConfiguration(SearchConfiguration.scanning())
                .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
                .eventBusImpl(IN_MEMORY)
                .build())
            .overrideWith(postgresExtension.getModule())
            .overrideWith((binder -> binder.bind(CleanupTasksPerformer.class).asEagerSingleton()))
            .overrideWith(new JmapSettingsProbeModule())
            .overrideWith(new DelegationProbeModule())
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

    private Module provideSaaSModule(boolean saasSupport) {
        if (saasSupport) {
            return Modules.combine(new PostgresSaaSModule(), new SaaSProbeModule());
        }
        return Modules.EMPTY_MODULE;
    }
}