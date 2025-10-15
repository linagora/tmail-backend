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

import static com.linagora.tmail.saas.rabbitmq.subscription.SaaSSubscriptionRabbitMQConfiguration.TWP_SAAS_SUBSCRIPTION_EXCHANGE_DEFAULT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.backends.cassandra.DockerCassandra.CASSANDRA_TESTING_PASSWORD;
import static org.apache.james.backends.cassandra.DockerCassandra.CASSANDRA_TESTING_USER;

import java.io.File;

import org.apache.james.CleanupTasksPerformer;
import org.apache.james.GuiceJamesServer;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.QuotaProbesImpl;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.github.fge.lambdas.Throwing;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.common.module.SaaSProbeModule;
import com.linagora.tmail.james.app.CassandraExtension;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedSaaSModule;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.common.JmapSaasContract;
import com.linagora.tmail.james.common.probe.DomainProbe;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.settings.TWPSettingsModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;

public class DistributedJmapSaaSTest implements JmapSaasContract {
    private static final com.linagora.tmail.james.app.RabbitMQExtension rabbitMQExtensionModule = new com.linagora.tmail.james.app.RabbitMQExtension();

    @RegisterExtension
    static DockerOpenSearchExtension opensearchExtension = new DockerOpenSearchExtension();

    @RegisterExtension
    static CassandraExtension cassandraExtension = new CassandraExtension();

    @RegisterExtension
    static AwsS3BlobStoreExtension s3Extension = new AwsS3BlobStoreExtension();

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.dockerRabbitMQ(rabbitMQExtensionModule.dockerRabbitMQ())
        .restartPolicy(RabbitMQExtension.DockerRestartPolicy.PER_CLASS)
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.WEAK);

    @TempDir
    private File tmpDir;

    private GuiceJamesServer guiceJamesServer;

    @Override
    public GuiceJamesServer startJmapServer(boolean saasSupport) {
        dropUserTable();
        guiceJamesServer = DistributedServer.createServer(DistributedJamesConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .blobStore(BlobStoreConfiguration.builder()
                    .s3()
                    .noSecondaryS3BlobStore()
                    .disableCache()
                    .deduplication()
                    .noCryptoConfig()
                    .disableSingleSave())
                .eventBusKeysChoice(EventBusKeysChoice.RABBITMQ)
                .searchConfiguration(SearchConfiguration.openSearch())
                .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
                .twpSettingsModuleChooserConfiguration(new TWPSettingsModuleChooserConfiguration(true))
                .build())
            .overrideWith(opensearchExtension.getModule(),
                cassandraExtension.getModule(),
                rabbitMQExtensionModule.getModule(),
                s3Extension.getModule())
            .overrideWith((binder -> binder.bind(CleanupTasksPerformer.class).asEagerSingleton()))
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(provideSaaSModule(saasSupport))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding()
                .to(DomainProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding()
                .to(QuotaProbesImpl.class));

        Throwing.runnable(() -> guiceJamesServer.start()).run();
        return guiceJamesServer;
    }

    @Override
    public void stopJmapServer() {
        if (guiceJamesServer != null && guiceJamesServer.isStarted()) {
            guiceJamesServer.stop();
        }
    }

    @AfterAll
    static void afterAll() {
        dropUserTable();
    }

    @Override
    public void publishAmqpSettingsMessage(String message, String routingKey) {
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(
                TWP_SAAS_SUBSCRIPTION_EXCHANGE_DEFAULT,
                routingKey,
                message.getBytes(UTF_8))))
            .block();
    }

    public static void dropUserTable() {
        Throwing.runnable(() -> {
            cassandraExtension.getCassandra().getRawContainer().execInContainer("cqlsh",
                "-u", CASSANDRA_TESTING_USER,
                "-p", CASSANDRA_TESTING_PASSWORD,
                "-e", "ALTER TABLE testing.\"user\" WITH gc_grace_seconds = 0;");

            cassandraExtension.getCassandra().getRawContainer().execInContainer("cqlsh",
                "-u", CASSANDRA_TESTING_USER,
                "-p", CASSANDRA_TESTING_PASSWORD,
                "-e", "DROP TABLE IF EXISTS testing.\"user\";");

            cassandraExtension.getCassandra().getRawContainer().execInContainer("cqlsh",
                "-u", CASSANDRA_TESTING_USER,
                "-p", CASSANDRA_TESTING_PASSWORD,
                "-e", "ALTER TABLE testing.\"domain\" WITH gc_grace_seconds = 0;");

            cassandraExtension.getCassandra().getRawContainer().execInContainer("cqlsh",
                "-u", CASSANDRA_TESTING_USER,
                "-p", CASSANDRA_TESTING_PASSWORD,
                "-e", "DROP TABLE IF EXISTS testing.\"domain\";");
        }).run();
    }

    private Module provideSaaSModule(boolean saasSupport) {
        if (saasSupport) {
            return Modules.combine(new DistributedSaaSModule(), new SaaSProbeModule());
        }
        return Modules.EMPTY_MODULE;
    }
}
