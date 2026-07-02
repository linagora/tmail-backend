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

package com.linagora.tmail.migration;

import org.apache.james.ExtraProperties;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerMain;
import org.apache.james.NaiveDelegationStoreModule;
import org.apache.james.data.UsersRepositoryModuleChooser;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.modules.BlobMemoryModule;
import org.apache.james.modules.LegacyEncryptionModule;
import org.apache.james.modules.MailetProcessingModule;
import org.apache.james.modules.RunArgumentsModule;
import org.apache.james.modules.TCNativeEncryptionModule;
import org.apache.james.modules.data.PostgresCommonModule;
import org.apache.james.modules.data.PostgresDataModule;
import org.apache.james.modules.data.PostgresUsersRepositoryModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.queue.memory.MemoryMailQueueModule;
import org.apache.james.modules.server.DNSServiceModule;
import org.apache.james.modules.server.DataRoutesModules;
import org.apache.james.modules.server.DefaultProcessorsConfigurationProviderModule;
import org.apache.james.modules.server.NoJwtModule;
import org.apache.james.modules.server.RawPostDequeueDecoratorModule;
import org.apache.james.modules.server.TaskManagerModule;
import org.apache.james.modules.server.WebAdminServerModule;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.linagora.tmail.migration.modules.MigratedUsersModule;
import com.linagora.tmail.migration.modules.MigrationProxyImapModule;

/**
 * Migration proxy server: a mailbox-less Twake Mail MTA assembled from our own Guice module set.
 *
 * <p>SMTP behaves as a standard relaying MTA whose {@code mailetcontainer.xml} routes mail per
 * recipient to the old / new / external backends; IMAP is a byte-proxy ({@link MigrationProxyImapModule})
 * relaying each authenticated connection to old/new. The migrated-users list (Postgres + webadmin)
 * drives both the SMTP recipient routing and the IMAP user routing.
 */
public class MigrationProxyServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationProxyServer.class);

    private static final Module PROTOCOLS = Modules.combine(
        new ProtocolHandlerModule(),
        new SMTPServerModule(),
        new WebAdminServerModule(),
        new DataRoutesModules(),
        new NoJwtModule(),
        new DefaultProcessorsConfigurationProviderModule(),
        new TaskManagerModule());

    private static final Module DATA = Modules.combine(
        new NaiveDelegationStoreModule(),
        new MailetProcessingModule(),
        new MemoryMailQueueModule(),
        new RawPostDequeueDecoratorModule(),
        new DNSServiceModule(),
        new BlobMemoryModule(),
        new PostgresCommonModule(),
        new PostgresDataModule(),
        PostgresUsersRepositoryModule.USER_CONFIGURATION_MODULE);

    private static final Module MIGRATION_PROXY = Modules.combine(
        new MigrationProxyImapModule(),
        new MigratedUsersModule());

    public static void main(String[] args) throws Exception {
        ExtraProperties.initialize();

        Configuration configuration = Configuration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();

        LOGGER.info("Loading configuration {}", configuration.toString());
        GuiceJamesServer server = createServer(configuration)
            .overrideWith(new RunArgumentsModule(args));

        JamesServerMain.main(server);
    }

    public static GuiceJamesServer createServer(Configuration configuration) {
        FileSystem fileSystem = new FileSystemImpl(configuration.directories());
        UsersRepositoryModuleChooser.Implementation usersRepositoryImplementation =
            UsersRepositoryModuleChooser.Implementation.parse(new FileConfigurationProvider(fileSystem, configuration));

        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(PROTOCOLS, DATA, MIGRATION_PROXY, chooseSslModule())
            .combineWith(new UsersRepositoryModuleChooser(new PostgresUsersRepositoryModule())
                .chooseModules(usersRepositoryImplementation));
    }

    private static Module chooseSslModule() {
        if (Boolean.getBoolean("james.tcnative.enabled")) {
            return new TCNativeEncryptionModule();
        }
        return new LegacyEncryptionModule();
    }
}
