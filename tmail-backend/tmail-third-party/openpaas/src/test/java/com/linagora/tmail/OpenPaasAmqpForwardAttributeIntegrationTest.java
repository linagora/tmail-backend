/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package com.linagora.tmail;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.backends.rabbitmq.Constants.AUTO_ACK;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Singleton;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.transport.mailets.ContactExtractor;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.SMTPAuthSuccessful;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMail;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.util.Modules;
import com.linagora.tmail.configuration.OpenPaasConfiguration;
import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngineModule;
import com.linagora.tmail.mailet.OpenPaasAmqpForwardAttribute;
import com.linagora.tmail.mailet.OpenPaasAmqpForwardAttributeConfig;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;

class OpenPaasAmqpForwardAttributeIntegrationTest {
    public static final String SENDER = "sender@" + DEFAULT_DOMAIN;
    public static final String TO = "to@" + DEFAULT_DOMAIN;
    public static final String EXTRACT_ATTRIBUTE = "ExtractedContacts";

    @RegisterExtension
    public static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.WEAK);

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;
    private Connection rabbitMQConnection;

    @BeforeEach
    void setUp() throws Exception {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setUri(rabbitMQExtension.getRabbitMQ().amqpUri());

        rabbitMQConnection = connectionFactory.newConnection();
    }

    @AfterEach
    void tearDown() throws IOException {
        System.setProperty("james.exit.on.startup.error", "true");
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
        if (rabbitMQConnection != null) {
            rabbitMQConnection.close();
        }
    }

    private Optional<String> getAmqpMessage(Channel rabbitMQChannel, String queueName)
        throws Exception {
        return Optional.ofNullable(rabbitMQChannel.basicGet(queueName, AUTO_ACK))
            .map(GetResponse::getBody)
            .map(value -> new String(value, StandardCharsets.UTF_8));
    }

    @Nested
    class WithOpenPaasModuleAndFanoutRabbitMqExchange {
        void setUpJamesServer(@TempDir File temporaryFolder, String exchange) throws Exception {
            MailetConfiguration amqpForwardAttributeMailet = MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(OpenPaasAmqpForwardAttribute.class)
                .addProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_PARAMETER_NAME, exchange)
                .addProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_TYPE_PARAMETER_NAME,
                    BuiltinExchangeType.FANOUT.getType())
                .addProperty(OpenPaasAmqpForwardAttributeConfig.ATTRIBUTE_PARAMETER_NAME,
                    EXTRACT_ATTRIBUTE)
                .build();

            jamesServer = TemporaryJamesServer.builder()
                .withBase(Modules.combine(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE,
                    new OpenPaasModule()))
                .withOverrides(new AbstractModule() {
                    @Provides
                    @Singleton
                    public OpenPaasConfiguration provideOpenPaasConfiguration()
                        throws URISyntaxException {
                        return new OpenPaasConfiguration(
                            AmqpUri.from(rabbitMQExtension.getRabbitMQ().amqpUri()),
                            URI.create("http://localhost:8081"),
                            "user",
                            "password"
                        );
                    }
                })
                .withOverrides(new InMemoryEmailAddressContactSearchEngineModule())
                .withMailetContainer(TemporaryJamesServer.defaultMailetContainerConfiguration()
                    .postmaster(SENDER)
                    .putProcessor(
                        ProcessorConfiguration.transport()
                            .addMailet(MailetConfiguration.builder()
                                .matcher(SMTPAuthSuccessful.class)
                                .mailet(ContactExtractor.class)
                                .addProperty(ContactExtractor.Configuration.ATTRIBUTE,
                                    EXTRACT_ATTRIBUTE))
                            .addMailet(amqpForwardAttributeMailet)
                            .addMailetsFrom(CommonProcessors.deliverOnlyTransport())))
                .build(temporaryFolder);

            jamesServer.start();
            jamesServer.getProbe(DataProbeImpl.class)
                .fluent()
                .addDomain(DEFAULT_DOMAIN)
                .addUser(SENDER, PASSWORD)
                .addUser(TO, PASSWORD);
        }

        @Test
        void recipientsShouldBePublishedToAmqpWhenSendingEmailWithExchangeTypeFanoutConfiguration(
            @TempDir File temporaryFolder) throws Exception {
            // Given AmqpForwardAttribute mailet configured with exchange type fanout
            String exchangeName = "collector:email" + UUID.randomUUID();
            String routingKey = "routing1" + UUID.randomUUID();

            MailetConfiguration amqpForwardAttributeMailet = MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(OpenPaasAmqpForwardAttribute.class)
                .addProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_PARAMETER_NAME,
                    exchangeName)
                .addProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_TYPE_PARAMETER_NAME,
                    BuiltinExchangeType.FANOUT.getType())
                .addProperty(OpenPaasAmqpForwardAttributeConfig.ATTRIBUTE_PARAMETER_NAME,
                    EXTRACT_ATTRIBUTE)
                .build();

            setUpJamesServer(temporaryFolder, exchangeName);

            Channel channel = rabbitMQConnection.createChannel();
            String queueName = channel.queueDeclare().getQueue();
            channel.queueBind(queueName, exchangeName, routingKey);

            // when sending an email
            messageSender.connect(LOCALHOST_IP,
                    jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
                .authenticate(SENDER, PASSWORD)
                .sendMessage(FakeMail.builder()
                    .name("name")
                    .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                        .setSender(SENDER)
                        .addToRecipient(TO)
                        .setSubject("Contact collection Rocks")
                        .setText("This is my email"))
                    .sender(SENDER)
                    .recipients(TO));

            // then the mailet should be published to AMQP
            testIMAPClient.connect(LOCALHOST_IP,
                    jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(TO, PASSWORD)
                .select(TestIMAPClient.INBOX)
                .awaitMessage(awaitAtMostOneMinute);

            Optional<String> actual = getAmqpMessage(channel, queueName);
            assertThat(actual).isNotEmpty();
            assertThatJson(actual.get()).isEqualTo("""
                {
                "userEmail" : "sender@james.org",
                "emails" : [ "to@james.org"]
                }
                """);
        }
    }

    @Nested
    class WithOpenPaasModuleAndDirectRabbitMqExchange {
        void setUpJamesServer(@TempDir File temporaryFolder, String exchange) throws Exception {
            MailetConfiguration amqpForwardAttributeMailet = MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(OpenPaasAmqpForwardAttribute.class)
                .addProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_PARAMETER_NAME, exchange)
                .addProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_TYPE_PARAMETER_NAME,
                    BuiltinExchangeType.DIRECT.getType())
                .addProperty(OpenPaasAmqpForwardAttributeConfig.ATTRIBUTE_PARAMETER_NAME,
                    EXTRACT_ATTRIBUTE)
                .build();

            jamesServer = TemporaryJamesServer.builder()
                .withBase(Modules.combine(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE,
                    new OpenPaasModule()))
                .withOverrides(new AbstractModule() {
                    @Provides
                    @Singleton
                    public OpenPaasConfiguration provideOpenPaasConfiguration()
                        throws URISyntaxException {
                        return new OpenPaasConfiguration(
                            AmqpUri.from(rabbitMQExtension.getRabbitMQ().amqpUri()),
                            URI.create("http://localhost:8081"),
                            "user",
                            "password"
                        );
                    }
                })
                .withOverrides(new InMemoryEmailAddressContactSearchEngineModule())
                .withMailetContainer(TemporaryJamesServer.defaultMailetContainerConfiguration()
                    .postmaster(SENDER)
                    .putProcessor(
                        ProcessorConfiguration.transport()
                            .addMailet(MailetConfiguration.builder()
                                .matcher(SMTPAuthSuccessful.class)
                                .mailet(ContactExtractor.class)
                                .addProperty(ContactExtractor.Configuration.ATTRIBUTE,
                                    EXTRACT_ATTRIBUTE))
                            .addMailet(amqpForwardAttributeMailet)
                            .addMailetsFrom(CommonProcessors.deliverOnlyTransport())))
                .build(temporaryFolder);

            jamesServer.start();
            jamesServer.getProbe(DataProbeImpl.class)
                .fluent()
                .addDomain(DEFAULT_DOMAIN)
                .addUser(SENDER, PASSWORD)
                .addUser(TO, PASSWORD);
        }

        @Test
        void mailetShouldWorkNormalWhenExchangeAlreadyExistsWithDifferentConfiguration(
            @TempDir File temporaryFolder) throws Exception {
            // Given an exchange already exists with different configuration
            String exchangeName = "collector:email" + UUID.randomUUID();
            String routingKey = "routing1" + UUID.randomUUID();

            Channel channel = rabbitMQConnection.createChannel();
            channel.exchangeDeclare(exchangeName, BuiltinExchangeType.FANOUT, true, true, null);
            String queueName = channel.queueDeclare().getQueue();
            channel.queueBind(queueName, exchangeName, routingKey);

            assertThatCode(() -> setUpJamesServer(temporaryFolder, exchangeName))
                .doesNotThrowAnyException();

            // When sending an email
            messageSender.connect(LOCALHOST_IP,
                    jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
                .authenticate(SENDER, PASSWORD)
                .sendMessage(FakeMail.builder()
                    .name("name")
                    .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                        .setSender(SENDER)
                        .addToRecipient(TO)
                        .setSubject("Contact collection Rocks")
                        .setText("This is my email"))
                    .sender(SENDER)
                    .recipients(TO));

            // Then the mailet should publish AQMP message
            testIMAPClient.connect(LOCALHOST_IP,
                    jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(TO, PASSWORD)
                .select(TestIMAPClient.INBOX)
                .awaitMessage(awaitAtMostOneMinute);

            Optional<String> actual = getAmqpMessage(channel, queueName);
            assertThat(actual).isNotEmpty();
            assertThatJson(actual.get()).isEqualTo("""
                {
                "userEmail" : "sender@james.org",
                "emails" : [ "to@james.org"]
                }
                """);
        }
    }

    @Nested
    class WithoutOpenPaasModule {
        void setUpJamesServer(@TempDir File temporaryFolder) throws Exception {
            MailetConfiguration amqpForwardAttribute = MailetConfiguration
                .builder()
                .matcher(All.class)
                .mailet(OpenPaasAmqpForwardAttribute.class)
                .addProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_PARAMETER_NAME, "dummy_exchange")
                .addProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_TYPE_PARAMETER_NAME,
                    BuiltinExchangeType.FANOUT.getType())
                .addProperty(OpenPaasAmqpForwardAttributeConfig.ATTRIBUTE_PARAMETER_NAME,
                    EXTRACT_ATTRIBUTE).build();

            jamesServer = TemporaryJamesServer.builder()
                .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
                .withOverrides(new InMemoryEmailAddressContactSearchEngineModule())
                .withMailetContainer(TemporaryJamesServer.defaultMailetContainerConfiguration()
                    .postmaster(SENDER)
                    .putProcessor(
                        ProcessorConfiguration.transport()
                            .addMailet(MailetConfiguration.builder()
                                .matcher(SMTPAuthSuccessful.class)
                                .mailet(ContactExtractor.class)
                                .addProperty(ContactExtractor.Configuration.ATTRIBUTE,
                                    EXTRACT_ATTRIBUTE))
                            .addMailet(amqpForwardAttribute)
                            .addMailetsFrom(CommonProcessors.deliverOnlyTransport())))
                .build(temporaryFolder);

            jamesServer.start();
        }

        @Test
        void serverShouldCrashWhenMailetLoadedButOpenPaasModuleNotEnabled(
            @TempDir File temporaryFolder) {
            // It is hard to detect/catch 'System.exit()' so we set the property to false to avoid the system exit.
            System.setProperty("james.exit.on.startup.error", "false");

            assertThatThrownBy(() -> setUpJamesServer(temporaryFolder))
                .hasRootCauseInstanceOf(MailetException.class)
                .hasRootCauseMessage(
                    "Failed to initialize mailet. OpenPaasModule is required to this mailet to function correctly.");
        }
    }
}