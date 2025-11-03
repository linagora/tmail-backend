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
package com.linagora.tmail.mailet.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;

import jakarta.mail.util.SharedByteArrayInputStream;

import org.apache.james.core.Username;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.mime.MessageContentExtractor;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;


public class EmailParserIntegrationTest {
    private static final Username BOB = Username.of("bob@test.com");
    private static final MailboxPath BOB_INBOX_PATH = MailboxPath.inbox(BOB);

    MessageManager bobInboxMessageManager;
    StoreMailboxManager mailboxManager;
    UpdatableTickingClock clock;
    MailboxSessionMapperFactory mapperFactory;
    MemoryEventDeadLetters eventDeadLetters;
    MailboxSession bobMailboxSession;
    MailboxId bobInboxId;

    private static final byte[] CONTENT = (
        "Subject: Test Subject\r\n" +
            "From: sender@example.com\r\n" +
            "To: recipient@example.com\r\n" +
            "Cc: cc@example.com\r\n" +
            "Date: Tue, 10 Oct 2023 10:00:00 +0000\r\n" +
            "\r\n" +
            "Body of the email").getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() throws MailboxException {
        clock = new UpdatableTickingClock(Instant.now());
        mapperFactory = new InMemoryMailboxSessionMapperFactory(clock);
        RetryBackoffConfiguration backoffConfiguration = RetryBackoffConfiguration.builder()
            .maxRetries(2)
            .firstBackoff(Duration.ofMillis(1))
            .jitterFactor(0.5)
            .build();
        eventDeadLetters = new MemoryEventDeadLetters();
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .eventBus(new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), backoffConfiguration, eventDeadLetters))
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();
        bobMailboxSession = MailboxSessionUtil.create(BOB);
        mailboxManager = resources.getMailboxManager();
        bobInboxId = mailboxManager.createMailbox(BOB_INBOX_PATH, bobMailboxSession).get();
        bobInboxMessageManager = mailboxManager.getMailbox(bobInboxId, bobMailboxSession);
    }

    @Test
    void reactiveEventShouldProcessAddedEventAndExtractContent() throws Exception , MailboxException , IOException {
        MessageManager.AppendResult appendResult = bobInboxMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)),
            bobMailboxSession);
        EmailParser emailParser = new EmailParser();

        Mono<String> cleaned = Mono.from(bobInboxMessageManager
            .getMessagesReactive(MessageRange.one(appendResult.getId().getUid()), FetchGroup.BODY_CONTENT, bobMailboxSession))// récupérer le premier MessageResult
            .flatMap(messageResult ->
                Mono.fromCallable(() -> {
                    DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();
                    messageBuilder.setMimeEntityConfig(MimeConfig.DEFAULT);
                    Message mimeMessage = messageBuilder.parseMessage(messageResult.getFullContent().getInputStream());
                    MessageContentExtractor.MessageContent extractor = new MessageContentExtractor().extract(mimeMessage);
                    return emailParser.cleanQuotedContent(extractor.getTextBody().get());
                }).subscribeOn(Schedulers.boundedElastic())
            );
        Assertions.assertEquals("Body of the email" ,cleaned.block());
    }

    @Test
    void reactiveEventShouldProcessAddedEventAndExtractContentWhenEmailContainResponse() throws Exception , MailboxException , IOException {
        MessageManager.AppendResult appendResult = bobInboxMessageManager.appendMessage(
            MessageManager.AppendCommand.from(ClassLoaderUtils.getSystemResourceAsSharedStream("Re_ Project Presentation – Task Coordination.eml")),
            bobMailboxSession);
        EmailParser emailParser = new EmailParser();

        Mono<String> cleaned = Mono.from(bobInboxMessageManager
                .getMessagesReactive(MessageRange.one(appendResult.getId().getUid()), FetchGroup.BODY_CONTENT, bobMailboxSession))// récupérer le premier MessageResult
            .flatMap(messageResult ->
                Mono.fromCallable(() -> {
                    DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();
                    messageBuilder.setMimeEntityConfig(MimeConfig.DEFAULT);
                    Message mimeMessage = messageBuilder.parseMessage(messageResult.getFullContent().getInputStream());
                    MessageContentExtractor.MessageContent extractor = new MessageContentExtractor().extract(mimeMessage);
                    return emailParser.cleanQuotedContent(extractor.getTextBody().get());
                }).subscribeOn(Schedulers.boundedElastic()));

        String expected = "Hi Ale,\n" +
        "Excellent, thank you for the quick follow-up and the data file. I've downloaded it and will begin the analysis shortly.\n" +
            "Sounds good. I aim to have a first draft to you by [mention a day, e.g., Wednesday afternoon] for your initial thoughts.\n" +
            "Talk soon,Sam";

        Assertions.assertEquals(normalize("Hi Ale,\n" +
            "Excellent, thank you for the quick follow-up and the data file. I've downloaded it and will begin the analysis shortly.\n" +
            "Sounds good. I aim to have a first draft to you by [mention a day, e.g., Wednesday afternoon] for your initial thoughts.\n" +
            "Talk soon,Sam") ,normalize(cleaned.block()));
    }

    @Test
    void reactiveEventShouldProcessAddedEventAndExtractContentWhenEmailContainResponseAndAttaachement() throws Exception , MailboxException , IOException {
        MessageManager.AppendResult appendResult = bobInboxMessageManager.appendMessage(
            MessageManager.AppendCommand.from(ClassLoaderUtils.getSystemResourceAsSharedStream("Re_ Question About Tomorrow’s Meeting.eml")),
            bobMailboxSession);
        EmailParser emailParser = new EmailParser();

        Mono<String> cleaned = Mono.from(bobInboxMessageManager
                .getMessagesReactive(MessageRange.one(appendResult.getId().getUid()), FetchGroup.BODY_CONTENT, bobMailboxSession))// récupérer le premier MessageResult
            .flatMap(messageResult ->
                Mono.fromCallable(() -> {
                    DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();
                    messageBuilder.setMimeEntityConfig(MimeConfig.DEFAULT);
                    Message mimeMessage = messageBuilder.parseMessage(messageResult.getFullContent().getInputStream());
                    MessageContentExtractor.MessageContent extractor = new MessageContentExtractor().extract(mimeMessage);
                    return emailParser.cleanQuotedContent(extractor.getTextBody().get());
                }).subscribeOn(Schedulers.boundedElastic()));
        String expected = """
            Hi Alae,
            
            
            Thanks for sharing your document. Please find attached the file I mentioned — it includes the points we’ll go over during tomorrow’s meeting.
            
            
            Let me know if you have any questions before then.
            
            
            Best,
            
            Sam""";

        Assertions.assertEquals(normalize(expected) ,normalize(cleaned.block()));
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC);
        n = n.replace("\r\n", "\n").replace("\r", "\n");
        return n.trim();
    }
}

