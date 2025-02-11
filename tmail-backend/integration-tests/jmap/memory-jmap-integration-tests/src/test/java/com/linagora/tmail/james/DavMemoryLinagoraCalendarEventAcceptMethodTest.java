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

import static com.linagora.tmail.configuration.OpenPaasConfiguration.OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.apache.james.jmap.JmapJamesServerContract.JAMES_SERVER_HOST;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Singleton;

import org.apache.commons.io.FileUtils;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.rfc8621.contract.Fixture;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.util.Modules;
import com.linagora.tmail.AmqpUri;
import com.linagora.tmail.CalDavEventAttendanceRepository;
import com.linagora.tmail.OpenPaasModuleChooserConfiguration;
import com.linagora.tmail.api.OpenPaasRestClient;
import com.linagora.tmail.configuration.DavConfiguration;
import com.linagora.tmail.configuration.OpenPaasConfiguration;
import com.linagora.tmail.dav.DavClient;
import com.linagora.tmail.dav.DavUserProvider;
import com.linagora.tmail.dav.OpenPaasDavUserProvider;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.calendar.acceptmethod.CalDavLinagoraCalendarEventAcceptMethodContract;
import com.linagora.tmail.james.common.calendar.LinagoraCalendarEventMethodContractUtilities$;
import com.linagora.tmail.james.jmap.EventAttendanceRepository;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class DavMemoryLinagoraCalendarEventAcceptMethodTest extends
    CalDavLinagoraCalendarEventAcceptMethodContract {


//    @RegisterExtension
//    @Order(1)
//    static DockerOpenPaasExtension dockerOpenPaasExtension = new DockerOpenPaasExtension();

    @RegisterExtension
    TestIMAPClient testIMAPClient = new TestIMAPClient();

    @RegisterExtension
    @Order(2)
    static JamesServerExtension
        jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir -> {
        // Copy all resources to the james configuration folder
        Path confPath = tmpDir.toPath().resolve("conf");
        Throwing.runnable(() -> FileUtils.copyDirectory(new File(ClassLoader.getSystemClassLoader().getResource(".").getFile()),
            confPath.toFile())).run();

        // Write content of mailetcontainer_with_amqpforward_openpass.xml to mailetcontainer.xml.
        Throwing.runnable(() ->
            FileUtils.copyToFile(
                new FileInputStream(confPath.resolve("mailetcontainer_openpaas_deployment.xml").toFile()),
                confPath.resolve("mailetcontainer.xml").toFile()
            )).run();
            return MemoryConfiguration.builder()
                .workingDirectory(tmpDir)
                .usersRepository(DEFAULT)
                .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
                .openPaasModuleChooserConfiguration(new OpenPaasModuleChooserConfiguration(
                    true, true, true))
                .build();
     }).server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule(), new DelegationProbeModule(),
                provideDavConfiguration(), provideDavUserProvider(), provideOpenPaasConfig(), provideEventAttendanceRepository()))
        .build();

    private static Module provideOpenPaasConfig() {
        return new AbstractModule() {
            @Provides
            @Singleton
            public OpenPaasConfiguration provideOpenPaasConfiguration(DavConfiguration davConfiguration)
                throws URISyntaxException {
                return new OpenPaasConfiguration(
                    URI.create("http://localhost:8080/api"),
                    "admin@open-paas.org",
                    "secret",
                    true,
                    Optional.of(new OpenPaasConfiguration.ContactConsumerConfiguration(
                        ImmutableList.of(AmqpUri.from("amqp://localhost:5672")),
                        OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED)),
                    Optional.of(davConfiguration));
            }
        };
    }

    private static Module provideDavConfiguration() {
        return new AbstractModule() {
            @Provides
            @Singleton
            public DavConfiguration provideDavConfiguration() {
                return new DavConfiguration(
                    new UsernamePasswordCredentials("admin", "secret123"),
                    Throwing.supplier(() -> URI.create("http://localhost:8001")).sneakyThrow().get(),
                    Optional.empty(),
                    Optional.empty());
            }
        };
    }

    private static Module provideEventAttendanceRepository() {
        return new AbstractModule() {
            @Provides
            public EventAttendanceRepository provideCalDavEventAttendanceRepository(DavClient davClient,
                                                                                    SessionProvider sessionProvider, MessageId.Factory messageIdFactory,
                                                                                    MessageIdManager messageIdManager,
                                                                                    DavUserProvider davUserProvider) {
                return new CalDavEventAttendanceRepository(davClient, sessionProvider, messageIdFactory, messageIdManager, davUserProvider);
            }
        };
    }

    private static Module provideDavUserProvider() {
        return new AbstractModule() {
            @Provides
            public DavUserProvider provideDavUserProvider(OpenPaasRestClient openPaasClient) {
                return new OpenPaasDavUserProvider(openPaasClient);
            }
        };
    }
}
