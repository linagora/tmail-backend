package com.linagora.tmail.mailet;

import static org.apache.james.mailets.configuration.CommonProcessors.ERROR_REPOSITORY;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.Constants;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.rate.limiter.memory.MemoryRateLimiterModule;
import org.apache.james.transport.mailets.ToRepository;
import org.apache.james.transport.matchers.All;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class OpenAiMailetIntegrationTest {
    private static final String BOB = "bob@" + DEFAULT_DOMAIN;
    private static final String ALICE = "alice@" + DEFAULT_DOMAIN;
    private static final String ANDRE = "andre@" + DEFAULT_DOMAIN;
    private static final String MARIA = "maria@" + DEFAULT_DOMAIN;
    private static final String CHAT_GPT_ADDRESS = "gpt@" + DEFAULT_DOMAIN;

    private TemporaryJamesServer jamesServer;

    @RegisterExtension
    public TestIMAPClient imapClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    @BeforeEach
    void setup(@TempDir File temporaryFolder) throws Exception {
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
                    .matcherCondition(CHAT_GPT_ADDRESS)
                    .mailet(OpenAIMailet.class)
                    .addProperty("apiKey", "demo")
                    .addProperty("gptAddress", CHAT_GPT_ADDRESS)
                    .build())
                .addMailetsFrom(CommonProcessors.transport()));

        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(mailetContainer)
            .withOverrides(new MemoryRateLimiterModule())
            .build(temporaryFolder);
        jamesServer.start();

        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(BOB, PASSWORD)
            .addUser(ALICE, PASSWORD)
            .addUser(ANDRE, PASSWORD)
            .addUser(MARIA, PASSWORD)
            .addUser(CHAT_GPT_ADDRESS, PASSWORD);
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

    @Test
    void shouldReplyToOriginalSender() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(BOB, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(BOB)
                    .addToRecipient(CHAT_GPT_ADDRESS)
                    .setSubject("How can I cook an egg?")
                    .setText("I do not know how to cook an egg. Please help me."))
                .sender(BOB)
                .recipient(CHAT_GPT_ADDRESS));

        awaitFirstMessage(BOB);

        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(imapClient.readFirstMessage());
        assertThat(mimeMessage.getHeader("From")).containsOnly(CHAT_GPT_ADDRESS);
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
                    .addToRecipient(CHAT_GPT_ADDRESS)
                    .setSubject("How can I cook an egg?")
                    .setContent(MimeMessageBuilder.multipartBuilder()
                        .subType("alternative")
                        .addBody(MimeMessageBuilder.bodyPartBuilder()
                            .data("I do not know how to cook an egg. Please help me.")))
                    .build())
                .sender(BOB)
                .recipient(CHAT_GPT_ADDRESS));

        awaitFirstMessage(BOB);

        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(imapClient.readFirstMessage());
        assertThat(mimeMessage.getHeader("From")).containsOnly(CHAT_GPT_ADDRESS);
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
                    .addToRecipient(CHAT_GPT_ADDRESS)
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
                .recipient(CHAT_GPT_ADDRESS));

        awaitFirstMessage(BOB);

        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(imapClient.readFirstMessage());
        assertThat(mimeMessage.getHeader("From")).containsOnly(CHAT_GPT_ADDRESS);
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
                    .addToRecipient(CHAT_GPT_ADDRESS)
                    .setSubject("How can I cook an egg?")
                    .setText("I do not know how to cook an egg. Please help me."))
                .sender(BOB)
                .recipients(ALICE, CHAT_GPT_ADDRESS));

        awaitMessages(ALICE, 2);

        String aliceRepliedMessage = imapClient.sendCommand("FETCH 2:2 (BODY[])");
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(aliceRepliedMessage);
        assertThat(mimeMessage.getHeader("From")).containsOnly(CHAT_GPT_ADDRESS);
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
                    .addToRecipient(CHAT_GPT_ADDRESS)
                    .addCcRecipient(ALICE)
                    .setSubject("How can I cook an egg?")
                    .setText("I do not know how to cook an egg. Please help me."))
                .sender(BOB)
                .recipients(ALICE, CHAT_GPT_ADDRESS));

        awaitMessages(ALICE, 2);

        String aliceRepliedMessage = imapClient.sendCommand("FETCH 2:2 (BODY[])");
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(aliceRepliedMessage);
        assertThat(mimeMessage.getHeader("From")).containsOnly(CHAT_GPT_ADDRESS);
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
                    .addToRecipient(CHAT_GPT_ADDRESS)
                    .addBccRecipient(ALICE)
                    .setSubject("How can I cook an egg?")
                    .setText("I do not know how to cook an egg. Please help me."))
                .sender(BOB)
                .recipients(ALICE, CHAT_GPT_ADDRESS));

        awaitMessages(ALICE, 2);

        String aliceRepliedMessage = imapClient.sendCommand("FETCH 2:2 (BODY[])");
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(aliceRepliedMessage);
        assertThat(mimeMessage.getHeader("From")).containsOnly(CHAT_GPT_ADDRESS);
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
                    .addToRecipient(ALICE, CHAT_GPT_ADDRESS)
                    .addCcRecipient(ANDRE)
                    .addBccRecipient(MARIA)
                    .setSubject("How can I cook an egg?")
                    .setText("I do not know how to cook an egg. Please help me."))
                .sender(BOB)
                .recipients(ALICE, ANDRE, MARIA, CHAT_GPT_ADDRESS));

        awaitMessages(BOB, 1);
        awaitMessages(ALICE, 2);
        awaitMessages(ANDRE, 2);
        awaitMessages(MARIA, 2);
    }

    @Test
    void shouldReplyOnlyWhenGptAddressIsInToHeader() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(BOB, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(BOB)
                    .addToRecipient(CHAT_GPT_ADDRESS)
                    .setSubject("How can I cook an egg?")
                    .setText("I do not know how to cook an egg. Please help me."))
                .sender(BOB)
                .recipient(CHAT_GPT_ADDRESS));

        awaitFirstMessage(BOB);
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(imapClient.readFirstMessage());
        assertThat(mimeMessage.getAllRecipients()).doesNotContain(new InternetAddress(CHAT_GPT_ADDRESS));
    }

    @Test
    void gptShouldNotReplyWhenNotSentToGptAddress() throws Exception {
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
}
