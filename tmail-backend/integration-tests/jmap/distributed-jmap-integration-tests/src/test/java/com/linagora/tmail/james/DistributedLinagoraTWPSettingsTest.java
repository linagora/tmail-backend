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

import static com.linagora.tmail.saas.rabbitmq.settings.TWPSettingsRabbitMQConfiguration.TWP_SETTINGS_EXCHANGE_DEFAULT;
import static com.linagora.tmail.saas.rabbitmq.settings.TWPSettingsRabbitMQConfiguration.TWP_SETTINGS_ROUTING_KEY_DEFAULT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.util.stream.Collectors;

import org.apache.james.CleanupTasksPerformer;
import org.apache.james.GuiceJamesServer;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.james.app.CassandraExtension;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.common.TWPSettingsContract;
import com.linagora.tmail.james.common.probe.JmapSettingsProbeModule;
import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration$;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.firebase.FirebasePushClient;
import com.linagora.tmail.james.jmap.settings.TWPReadOnlyPropertyProvider;
import com.linagora.tmail.james.jmap.settings.TWPSettingsModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import scala.collection.immutable.Map;
import scala.jdk.javaapi.CollectionConverters;

public class DistributedLinagoraTWPSettingsTest implements TWPSettingsContract {
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
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.STRONG);

    @TempDir
    private File tmpDir;

    private GuiceJamesServer guiceJamesServer;

    @Override
    public GuiceJamesServer startJmapServer(Map<String, Object> overrideJmapProperties) {
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
                .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.ENABLED)
                .twpSettingsModuleChooserConfiguration(twpSettingsModuleChooserConfiguration(CollectionConverters.asJava(overrideJmapProperties)))
                .build())
            .overrideWith(opensearchExtension.getModule(),
                cassandraExtension.getModule(),
                rabbitMQExtensionModule.getModule(),
                s3Extension.getModule())
            .overrideWith((binder -> binder.bind(CleanupTasksPerformer.class).asEagerSingleton()))
            .overrideWith(new JmapSettingsProbeModule())
            .overrideWith(new DelegationProbeModule())
            .overrideWith(binder -> binder.bind(FirebasePushClient.class).toInstance(TWPSettingsContract.firebasePushClient()))
            .overrideWith(new LinagoraTestJMAPServerModule(CollectionConverters.asJava(overrideJmapProperties)));

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
    public void publishAmqpSettingsMessage(String message) {
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(
                TWP_SETTINGS_EXCHANGE_DEFAULT,
                TWP_SETTINGS_ROUTING_KEY_DEFAULT,
                message.getBytes(UTF_8))))
            .block();
    }

    private TWPSettingsModuleChooserConfiguration twpSettingsModuleChooserConfiguration(java.util.Map<String, Object> overrideJmapProperties) {
        java.util.Map<String, String> stringMap = overrideJmapProperties.entrySet().stream()
            .filter(entry -> entry.getValue() instanceof String)
            .collect(Collectors.toMap(java.util.Map.Entry::getKey,
                entry -> (String) entry.getValue()));

        if (stringMap.getOrDefault(JMAPExtensionConfiguration$.MODULE$.SETTINGS_READONLY_PROPERTIES_PROVIDERS(), "")
            .contains(TWPReadOnlyPropertyProvider.class.getSimpleName())) {
            return new TWPSettingsModuleChooserConfiguration(true);
        } else {
            return new TWPSettingsModuleChooserConfiguration(false);
        }
    }
}
