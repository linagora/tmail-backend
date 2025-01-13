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

import static com.linagora.tmail.OpenPaasModuleChooserConfiguration.ENABLED;
import static com.linagora.tmail.OpenPaasModuleChooserConfiguration.ENABLE_CARDDAV;
import static com.linagora.tmail.OpenPaasModuleChooserConfiguration.ENABLE_CONTACTS_CONSUMER;
import static com.linagora.tmail.configuration.OpenPaasConfiguration.OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED;
import static com.linagora.tmail.configuration.OpenPaasConfiguration.OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_DISABLED;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Singleton;

import org.apache.commons.io.FileUtils;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.transport.mailets.amqp.AmqpExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.linagora.tmail.AmqpUri;
import com.linagora.tmail.OpenPaasModuleChooserConfiguration;
import com.linagora.tmail.configuration.OpenPaasConfiguration;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.LinagoraCalendarEventReplyWithAMQPWorkflowContract;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import com.rabbitmq.client.BuiltinExchangeType;

public class MemoryCalendarEventReplyWithAMQPWorkflowTest implements LinagoraCalendarEventReplyWithAMQPWorkflowContract {
    static final String AMQP_EXCHANGE_NAME = "james:events";
    static final String AMQP_ROUTING_KEY = "icalendar_routing_key2";

    @RegisterExtension
    public static AmqpExtension amqpExtension = new AmqpExtension(AMQP_EXCHANGE_NAME, AMQP_ROUTING_KEY, BuiltinExchangeType.FANOUT);

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(
        tmpDir -> {
            // Copy all resources to the james configuration folder
            Path confPath = tmpDir.toPath().resolve("conf");
            Throwing.runnable(() -> FileUtils.copyDirectory(new File(ClassLoader.getSystemClassLoader().getResource(".").getFile()),
                confPath.toFile())).run();

            // Write content of mailetcontainer_with_amqpforward_openpass.xml to mailetcontainer.xml.
            Throwing.runnable(() ->
                FileUtils.copyToFile(
                    new FileInputStream(confPath.resolve("mailetcontainer_with_amqpforward_openpass.xml").toFile()),
                    confPath.resolve("mailetcontainer.xml").toFile()
                )).run();

            return MemoryConfiguration.builder()
                .workingDirectory(tmpDir)
                .usersRepository(DEFAULT)
                .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
                .openPaasModuleChooserConfiguration(new OpenPaasModuleChooserConfiguration(ENABLED, !ENABLE_CARDDAV, ENABLE_CONTACTS_CONSUMER))
                .build();
        })
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(
                new LinagoraTestJMAPServerModule(),
                new DelegationProbeModule(),
                provideOpenPaasConfigurationModule(amqpExtension.getAmqpUri()))
        )
        .build();

    @Override
    public String randomBlobId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public Optional<String> readAMQPContent() {
        return Throwing.supplier(() -> amqpExtension.readContent()).get();
    }

    private static Module provideOpenPaasConfigurationModule(String amqpUri) {
        return new AbstractModule() {
            @Provides
            @Singleton
            public OpenPaasConfiguration provideOpenPaasConfiguration() {
                return new OpenPaasConfiguration(
                    URI.create("http://localhost:8081"),
                    "user",
                    "password",
                    OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_DISABLED,
                    new OpenPaasConfiguration.ContactConsumerConfiguration(
                        ImmutableList.of(AmqpUri.from(amqpUri)),
                        OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED));
            }
        };

    }
}
