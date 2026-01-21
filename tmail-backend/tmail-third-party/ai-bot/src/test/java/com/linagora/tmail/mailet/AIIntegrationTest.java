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

package com.linagora.tmail.mailet;

import static com.linagora.tmail.mailet.AIBotMailetTest.DEMO_MODEL;
import static org.apache.james.mailets.configuration.CommonProcessors.ERROR_REPOSITORY;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.Constants;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.transport.mailets.ToRepository;
import org.apache.james.transport.matchers.All;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.inject.name.Names;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.probe.JmapSettingsProbe;
import com.linagora.tmail.james.common.probe.JmapSettingsProbeModule;
import com.linagora.tmail.mailet.conf.AIBaseModule;

@Disabled("Unstable test https://github.com/linagora/tmail-backend/issues/1303")
class AIIntegrationTest {
    private static final String BOB = "bob@" + DEFAULT_DOMAIN;
    private static final String ALICE = "alice@" + DEFAULT_DOMAIN;
    private static final String ANDRE = "andre@" + DEFAULT_DOMAIN;
    private static final String MARIA = "maria@" + DEFAULT_DOMAIN;
    private static final String BOT_ADDRESS = "gpt@" + DEFAULT_DOMAIN;

    private TemporaryJamesServer jamesServer;

    @RegisterExtension
    public TestIMAPClient imapClient = new TestIMAPClient();

    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    @BeforeEach
    void setup(@TempDir File temporaryFolder) throws Exception {
        // Copy the custom resource `ai.properties` to the `conf/` directory
        // used by James temporary server
        String resourceName = "ai.properties";
        Path resourcesFolder = Paths.get(temporaryFolder.getAbsolutePath(), "conf");
        Path resolvedResource = resourcesFolder.resolve(resourceName);
        String content = "apiKey=demo\n"
                       + "baseURL=\n"
                       + "model=" + DEMO_MODEL + "\n";
        Files.createDirectories(resourcesFolder);
        Files.writeString(resolvedResource, content);

        String listenersContent = """
            <listeners>
                <listener>
                    <class>com.linagora.tmail.listener.rag.LlmMailPrioritizationClassifierListener</class>
                </listener>
            </listeners>
            """;
        Files.writeString(resourcesFolder.resolve("listeners.xml"), listenersContent);

        MailetContainer.Builder mailetContainer = TemporaryJamesServer.simpleMailetContainerConfiguration()
            .putProcessor(ProcessorConfiguration.error()
                .enableJmx(false)
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ToRepository.class)
                    .addProperty("repositoryPath", ERROR_REPOSITORY.asString()))
                .build())
            .putProcessor(ProcessorConfiguration.transport()
                .addMailet(MailetConfiguration.builder()
                    .matcher(RecipientsContain.class)
                    .matcherCondition(BOT_ADDRESS)
                    .mailet(AIBotMailet.class)
                    .addProperty("botAddress", BOT_ADDRESS)
                    .build())
                .addMailetsFrom(CommonProcessors.transport()));

        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryServer.MODULES)
            .withOverrides(new AIBaseModule(), new JmapSettingsProbeModule(),
                binder -> binder.bind(Boolean.class).annotatedWith(Names.named("calDavSupport")).toInstance(false))
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder);
        jamesServer.start();

        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(BOB, PASSWORD)
            .addUser(ALICE, PASSWORD)
            .addUser(ANDRE, PASSWORD)
            .addUser(MARIA, PASSWORD)
            .addUser(BOT_ADDRESS, PASSWORD);

        jamesServer.getProbe(JmapSettingsProbe.class)
            .reset(Username.of(ALICE), Map.of("ai.needs-action.enabled", "true"));
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    private void awaitMessages(String username, int count) throws IOException {
        imapClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(username, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessageCount(Constants.awaitAtMostOneMinute, count);
    }

    private void awaitFirstMessage(String username) throws IOException {
        awaitMessages(username, 1);
    }

    private void awaitFirstMessageWithFlag(String username, String flag) throws IOException {
        imapClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(username, PASSWORD)
            .select(TestIMAPClient.INBOX);

        Constants.awaitAtMostOneMinute.untilAsserted(() -> assertThat(imapClient.hasAMessageWithFlags(flag))
            .isTrue());
    }

    @Test
    void shouldReplyToOriginalSender() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(BOB, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(BOB)
                    .addToRecipient(BOT_ADDRESS)
                    .setSubject("How can I cook an egg?")
                    .setText("I do not know how to cook an egg. Please help me."))
                .sender(BOB)
                .recipient(BOT_ADDRESS));

        awaitFirstMessage(BOB);

        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(imapClient.readFirstMessage());
        assertThat(mimeMessage.getHeader("From")).containsOnly(BOT_ADDRESS);
        assertThat(mimeMessage.getHeader("To")).containsOnly(BOB);
        assertThat(mimeMessage.getHeader("Subject")[0]).isEqualTo("Re: How can I cook an egg?");
    }

    @Test
    void shouldHandleMultipartMailWell() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(BOB, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(BOB)
                    .addToRecipient(BOT_ADDRESS)
                    .setSubject("How can I cook an egg?")
                    .setContent(MimeMessageBuilder.multipartBuilder()
                        .subType("alternative")
                        .addBody(MimeMessageBuilder.bodyPartBuilder()
                            .data("I do not know how to cook an egg. Please help me.")))
                    .build())
                .sender(BOB)
                .recipient(BOT_ADDRESS));

        awaitFirstMessage(BOB);

        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(imapClient.readFirstMessage());
        assertThat(mimeMessage.getHeader("From")).containsOnly(BOT_ADDRESS);
        assertThat(mimeMessage.getHeader("To")).containsOnly(BOB);
        assertThat(mimeMessage.getHeader("Subject")[0]).isEqualTo("Re: How can I cook an egg?");
    }

    @Test
    void shouldHandleMailWithHtmlBodyWell() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(BOB, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(BOB)
                    .addToRecipient(BOT_ADDRESS)
                    .setSubject("How can I cook an egg?")
                    .setContent(MimeMessageBuilder.multipartBuilder()
                        .subType("alternative")
                        .addBody(MimeMessageBuilder.bodyPartBuilder()
                            .data("""
                                <html>
                                  <body>
                                    <p>I do not know how to cook an egg. Please help me</p>
                                  </body>
                                </html>""")
                            .addHeader("Content-Type", "text/html")))
                    .build())
                .sender(BOB)
                .recipient(BOT_ADDRESS));

        awaitFirstMessage(BOB);

        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(imapClient.readFirstMessage());
        assertThat(mimeMessage.getHeader("From")).containsOnly(BOT_ADDRESS);
        assertThat(mimeMessage.getHeader("To")).containsOnly(BOB);
        assertThat(mimeMessage.getHeader("Subject")[0]).isEqualTo("Re: How can I cook an egg?");
    }

    @Test
    void shouldReplyToRecipientsInToHeader() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(BOB, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(BOB)
                    .addToRecipient(ALICE)
                    .addToRecipient(BOT_ADDRESS)
                    .setSubject("How can I cook an egg?")
                    .setText("I do not know how to cook an egg. Please help me."))
                .sender(BOB)
                .recipients(ALICE, BOT_ADDRESS));

        awaitMessages(ALICE, 2);

        String aliceRepliedMessage = imapClient.sendCommand("FETCH 2:2 (BODY[])");
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(aliceRepliedMessage);
        assertThat(mimeMessage.getHeader("From")).containsOnly(BOT_ADDRESS);
        assertThat(mimeMessage.getHeader("To")).containsOnly(BOB + ", " + ALICE);
        assertThat(mimeMessage.getHeader("Subject")[0]).isEqualTo("Re: How can I cook an egg?");
    }

    @Test
    void shouldReplyToRecipientsInCcHeader() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(BOB, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(BOB)
                    .addToRecipient(BOT_ADDRESS)
                    .addCcRecipient(ALICE)
                    .setSubject("How can I cook an egg?")
                    .setText("I do not know how to cook an egg. Please help me."))
                .sender(BOB)
                .recipients(ALICE, BOT_ADDRESS));

        awaitMessages(ALICE, 2);

        String aliceRepliedMessage = imapClient.sendCommand("FETCH 2:2 (BODY[])");
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(aliceRepliedMessage);
        assertThat(mimeMessage.getHeader("From")).containsOnly(BOT_ADDRESS);
        assertThat(mimeMessage.getHeader("Cc")).containsOnly(ALICE);
        assertThat(mimeMessage.getHeader("Subject")[0]).isEqualTo("Re: How can I cook an egg?");
    }

    @Test
    void shouldReplyToRecipientsInBccHeader() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(BOB, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(BOB)
                    .addToRecipient(BOT_ADDRESS)
                    .addBccRecipient(ALICE)
                    .setSubject("How can I cook an egg?")
                    .setText("I do not know how to cook an egg. Please help me."))
                .sender(BOB)
                .recipients(ALICE, BOT_ADDRESS));

        awaitMessages(ALICE, 2);

        String aliceRepliedMessage = imapClient.sendCommand("FETCH 2:2 (BODY[])");
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(aliceRepliedMessage);
        assertThat(mimeMessage.getHeader("From")).containsOnly(BOT_ADDRESS);
        assertThat(mimeMessage.getHeader("Subject")[0]).isEqualTo("Re: How can I cook an egg?");
    }

    @Test
    void shouldReplyToRecipientsWhenMixedCaseToAndCcAndBcc() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(BOB, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(BOB)
                    .addToRecipient(ALICE, BOT_ADDRESS)
                    .addCcRecipient(ANDRE)
                    .addBccRecipient(MARIA)
                    .setSubject("How can I cook an egg?")
                    .setText("I do not know how to cook an egg. Please help me."))
                .sender(BOB)
                .recipients(ALICE, ANDRE, MARIA, BOT_ADDRESS));

        awaitMessages(BOB, 1);
        awaitMessages(ALICE, 2);
        awaitMessages(ANDRE, 2);
        awaitMessages(MARIA, 2);
    }

    @Test
    void shouldReplyOnlyWhenBotAddressIsInToHeader() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(BOB, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(BOB)
                    .addToRecipient(BOT_ADDRESS)
                    .setSubject("How can I cook an egg?")
                    .setText("I do not know how to cook an egg. Please help me."))
                .sender(BOB)
                .recipient(BOT_ADDRESS));

        awaitFirstMessage(BOB);
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(imapClient.readFirstMessage());
        assertThat(mimeMessage.getAllRecipients()).doesNotContain(new InternetAddress(BOT_ADDRESS));
    }

    @Test
    void botShouldNotReplyWhenNotSentToBotAddress() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(BOB, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(BOB)
                    .addToRecipient(ALICE)
                    .setSubject("How can I cook an egg?")
                    .setText("I do not know how to cook an egg. Please help me."))
                .sender(BOB)
                .recipient(ALICE));

        awaitMessages(BOB, 0);
        awaitMessages(ALICE, 1);
    }

    @Test
    void urgentMailShouldBeTaggedNeedsActionKeyword() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(BOB, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("urgent")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(BOB)
                    .addToRecipient(ALICE)
                    .setSubject("URGENT – Production API Failure")
                    .setText("""
                        Hi team,
                        Our payment gateway API has been failing since 03:12 AM UTC. All customer transactions are currently being rejected. We need an immediate fix or rollback before peak traffic starts in 2 hours.
                        Please acknowledge as soon as possible.
                        Thanks,
                        Robert
                        """))
                .sender(BOB)
                .recipient(ALICE));

        awaitFirstMessageWithFlag(ALICE, "needs-action");
    }
}
