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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

/**
 * This class is copied & adapted from {@link org.apache.james.jmap.event.ComputeMessageFastViewProjectionListenerTest}
 */

package com.linagora.tmail.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.UUID;

import org.apache.james.backends.opensearch.DockerOpenSearchExtension;
import org.apache.james.backends.opensearch.IndexName;
import org.apache.james.backends.opensearch.OpenSearchConfiguration;
import org.apache.james.backends.opensearch.OpenSearchIndexer;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.backends.opensearch.ReadAliasName;
import org.apache.james.backends.opensearch.WriteAliasName;
import org.apache.james.core.Username;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.jmap.memory.projections.MemoryMessageFastViewProjection;
import org.apache.james.jmap.utils.JsoupHtmlTextExtractor;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.opensearch.IndexAttachments;
import org.apache.james.mailbox.opensearch.IndexBody;
import org.apache.james.mailbox.opensearch.IndexHeaders;
import org.apache.james.mailbox.opensearch.MailboxIdRoutingKeyFactory;
import org.apache.james.mailbox.opensearch.MailboxIndexCreationUtil;
import org.apache.james.mailbox.opensearch.OpenSearchMailboxConfiguration;
import org.apache.james.mailbox.opensearch.events.OpenSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.opensearch.json.MessageToOpenSearchJson;
import org.apache.james.mailbox.opensearch.query.QueryConverter;
import org.apache.james.mailbox.opensearch.search.OpenSearchSearcher;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.html.HtmlTextExtractor;
import org.apache.james.util.mime.MessageContentExtractor;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.mailbox.opensearch.TmailCriterionConverter;
import com.linagora.tmail.mailbox.opensearch.TmailMailboxMappingFactory;
import com.linagora.tmail.mailbox.opensearch.TmailOpenSearchMailboxConfiguration;

import reactor.core.publisher.Mono;

class TMailComputeMessageFastViewProjectionListenerTest {
    @RegisterExtension
    static DockerOpenSearchExtension openSearch = new DockerOpenSearchExtension(DockerOpenSearchExtension.CleanupStrategy.NONE);

    private static final Username BOB = Username.of("bob");
    private static final Preview PREVIEW = Preview.from("blabla bloblo");
    private static final MailboxPath BOB_INBOX_PATH = MailboxPath.inbox(BOB);
    private static final MailboxPath BOB_OTHER_BOX_PATH = MailboxPath.forUser(BOB, "otherBox");
    private static final MessageFastViewPrecomputedProperties PRECOMPUTED_PROPERTIES_PREVIEW = MessageFastViewPrecomputedProperties.builder()
        .preview(PREVIEW)
        .noAttachments()
        .build();
    private static final MessageFastViewPrecomputedProperties PRECOMPUTED_PROPERTIES_EMPTY = MessageFastViewPrecomputedProperties.builder()
        .preview(Preview.from(""))
        .noAttachments()
        .build();
    private static final MessageFastViewPrecomputedProperties PRECOMPUTED_PROPERTIES_PREVIEW_HAS_ATTACHMENT = MessageFastViewPrecomputedProperties.builder()
        .preview(PREVIEW)
        .hasAttachment()
        .build();
    private static final MessageFastViewPrecomputedProperties PRECOMPUTED_PROPERTIES_HAS_ATTACHMENT = MessageFastViewPrecomputedProperties.builder()
        .preview(Preview.from(""))
        .hasAttachment()
        .build();
    private static final int SEARCH_SIZE = 1;

    static ReactorOpenSearchClient client;

    MessageFastViewProjection messageFastViewProjection;
    MailboxSession mailboxSession;
    StoreMailboxManager mailboxManager;
    MessageManager inboxMessageManager;
    MessageManager otherBoxMessageManager;
    MessageIdManager messageIdManager;
    MemoryEventDeadLetters eventDeadLetters;
    ReadAliasName readAliasName;
    WriteAliasName writeAliasName;
    IndexName indexName;
    ComputePreviewMessageIndexer computePreviewMessageIndexer;

    @BeforeAll
    static void setUpAll() {
        client = openSearch.getDockerOpenSearch().clientProvider().get();
    }

    @BeforeEach
    void setup() throws Exception {
        readAliasName = new ReadAliasName(UUID.randomUUID().toString());
        writeAliasName = new WriteAliasName(UUID.randomUUID().toString());
        indexName = new IndexName(UUID.randomUUID().toString());
        OpenSearchConfiguration openSearchConfiguration = openSearch.getDockerOpenSearch().configuration();
        MailboxIndexCreationUtil.prepareClient(
            client, readAliasName, writeAliasName, indexName,
            openSearchConfiguration, new TmailMailboxMappingFactory(openSearchConfiguration, TmailOpenSearchMailboxConfiguration.DEFAULT_CONFIGURATION));
        eventDeadLetters = new MemoryEventDeadLetters();
        // Default RetryBackoffConfiguration leads each events to be re-executed for 30s which is too long
        // Reducing the wait time for the event bus allow a faster test suite execution without harming test correctness
        RetryBackoffConfiguration backoffConfiguration = RetryBackoffConfiguration.builder()
            .maxRetries(2)
            .firstBackoff(Duration.ofMillis(1))
            .jitterFactor(0.5)
            .build();

        MailboxIdRoutingKeyFactory routingKeyFactory = new MailboxIdRoutingKeyFactory();
        MessageContentExtractor messageContentExtractor = new MessageContentExtractor();
        HtmlTextExtractor htmlTextExtractor = new JsoupHtmlTextExtractor();
        messageFastViewProjection = spy(new MemoryMessageFastViewProjection(new RecordingMetricFactory()));
        computePreviewMessageIndexer = spy(new ComputePreviewMessageIndexer(messageFastViewProjection,
            new MessageFastViewPrecomputedProperties.Factory(new Preview.Factory(messageContentExtractor, htmlTextExtractor))));

        OpenSearchMailboxConfiguration notIndexBodyOpenSearchMailboxConfiguration = OpenSearchMailboxConfiguration.builder()
            .indexBody(IndexBody.NO)
            .build();

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .eventBus(new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), backoffConfiguration, eventDeadLetters))
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .listeningSearchIndex(preInstanciationStage -> new OpenSearchListeningMessageSearchIndex(
                preInstanciationStage.getMapperFactory(),
                ImmutableSet.of(),
                new OpenSearchIndexer(client,
                    writeAliasName),
                new OpenSearchSearcher(client, new QueryConverter(new TmailCriterionConverter(notIndexBodyOpenSearchMailboxConfiguration, TmailOpenSearchMailboxConfiguration.DEFAULT_CONFIGURATION)),
                    SEARCH_SIZE, readAliasName, routingKeyFactory),
                new MessageToOpenSearchJson(new DefaultTextExtractor(), ZoneId.of("Europe/Paris"), IndexAttachments.YES, IndexHeaders.YES),
                preInstanciationStage.getSessionProvider(), routingKeyFactory, new InMemoryMessageId.Factory(),
                notIndexBodyOpenSearchMailboxConfiguration, new RecordingMetricFactory(),
                ImmutableSet.of(computePreviewMessageIndexer)))
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        mailboxManager = resources.getMailboxManager();
        messageIdManager = spy(resources.getMessageIdManager());
        FakeAuthenticator authenticator = new FakeAuthenticator();
        authenticator.addUser(BOB, "12345");

        mailboxSession = MailboxSessionUtil.create(BOB);

        MailboxId inboxId = mailboxManager.createMailbox(BOB_INBOX_PATH, mailboxSession).get();
        inboxMessageManager = mailboxManager.getMailbox(inboxId, mailboxSession);

        MailboxId otherBoxId = mailboxManager.createMailbox(BOB_OTHER_BOX_PATH, mailboxSession).get();
        otherBoxMessageManager = mailboxManager.getMailbox(otherBoxId, mailboxSession);
    }

    @Test
    void shouldStorePreviewWithNoAttachmentsWhenBodyMessageNotEmptyAndNoAttachments() throws Exception {
        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(previewMessage()),
            mailboxSession).getId();

        assertThat(Mono.from(messageFastViewProjection.retrieve(composedId.getMessageId())).block())
            .isEqualTo(PRECOMPUTED_PROPERTIES_PREVIEW);
    }

    @Test
    void shouldStoreEmptyPreviewWithNoAttachmentsWhenEmptyBodyMessageAndNoAttachments() throws Exception {
        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(emptyMessage()),
            mailboxSession).getId();

        assertThat(Mono.from(messageFastViewProjection.retrieve(composedId.getMessageId())).block())
            .isEqualTo(PRECOMPUTED_PROPERTIES_EMPTY);
    }

    @Test
    void shouldStorePreviewWithHasAttachmentWhenBodyMessageNotEmptyAndHasAttachment() throws Exception {
        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("fullMessage.eml")),
            mailboxSession).getId();

        assertThat(Mono.from(messageFastViewProjection.retrieve(composedId.getMessageId())).block())
            .isEqualTo(PRECOMPUTED_PROPERTIES_PREVIEW_HAS_ATTACHMENT);
    }

    @Test
    void shouldStoreEmptyPreviewWithHasAttachmentWhenEmptyBodyMessageAndHasAttachment() throws Exception {
        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("emptyBodyMessageWithOneAttachment.eml")),
            mailboxSession).getId();

        assertThat(Mono.from(messageFastViewProjection.retrieve(composedId.getMessageId())).block())
            .isEqualTo(PRECOMPUTED_PROPERTIES_HAS_ATTACHMENT);
    }

    @Test
    void shouldStoreMultiplePreviewsWhenMultipleMessagesAdded() throws Exception {
        ComposedMessageId composedId1 = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(previewMessage()),
            mailboxSession).getId();

        ComposedMessageId composedId2 = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(emptyMessage()),
            mailboxSession).getId();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Mono.from(messageFastViewProjection.retrieve(composedId1.getMessageId())).block())
                .isEqualTo(PRECOMPUTED_PROPERTIES_PREVIEW);
            softly.assertThat(Mono.from(messageFastViewProjection.retrieve(composedId2.getMessageId())).block())
                .isEqualTo(PRECOMPUTED_PROPERTIES_EMPTY);
        });

        verify(messageFastViewProjection, times(2)).store(any(), any());
    }

    @Test
    void shouldKeepPreviewWhenMovingMessage() throws Exception {
        inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(previewMessage()),
            mailboxSession);

        mailboxManager.moveMessages(MessageRange.all(), BOB_INBOX_PATH, BOB_OTHER_BOX_PATH, mailboxSession);

        MessageResult result = otherBoxMessageManager.getMessages(MessageRange.all(), FetchGroup.MINIMAL, mailboxSession).next();
        assertThat(Mono.from(messageFastViewProjection.retrieve(result.getMessageId())).block())
            .isEqualTo(PRECOMPUTED_PROPERTIES_PREVIEW);
    }

    @Test
    void shouldKeepPreviewWhenCopyingMessage() throws Exception {
        inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(previewMessage()),
            mailboxSession);

        mailboxManager.copyMessages(MessageRange.all(), BOB_INBOX_PATH, BOB_OTHER_BOX_PATH, mailboxSession);

        MessageResult result = otherBoxMessageManager.getMessages(MessageRange.all(), FetchGroup.MINIMAL, mailboxSession).next();
        assertThat(Mono.from(messageFastViewProjection.retrieve(result.getMessageId())).block())
            .isEqualTo(PRECOMPUTED_PROPERTIES_PREVIEW);
    }

    @Test
    void shouldNotDuplicateStorePreviewWhenCopyingMessage() throws Exception {
        MessageManager.AppendResult appendResult = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(previewMessage()),
            mailboxSession);

        mailboxManager.copyMessages(MessageRange.all(), BOB_INBOX_PATH, BOB_OTHER_BOX_PATH, mailboxSession);

        verify(messageFastViewProjection, times(1)).store(eq(appendResult.getId().getMessageId()), any());
    }

    @Test
    void shouldNotDuplicateStorePreviewWhenMovingMessage() throws Exception {
        MessageManager.AppendResult appendResult = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(previewMessage()),
            mailboxSession);

        mailboxManager.moveMessages(MessageRange.all(), BOB_INBOX_PATH, BOB_OTHER_BOX_PATH, mailboxSession);

        verify(messageFastViewProjection, times(1)).store(eq(appendResult.getId().getMessageId()), any());
    }

    @Test
    void shouldStoreEventInDeadLettersByOpenSearchGroupWhenComputeFastViewPrecomputedPropertiesException() throws Exception {
        doThrow(new IOException())
            .when(computePreviewMessageIndexer)
            .computeFastViewPrecomputedProperties(any());

        inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(previewMessage()),
            mailboxSession);

        assertThat(eventDeadLetters.failedIds(new OpenSearchListeningMessageSearchIndex.OpenSearchListeningMessageSearchIndexGroup()).collectList().block())
            .hasSize(1);
    }

    @Test
    void shouldKeepPreviewWhenExpungedAndStillReferenced() throws Exception {
        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("fullMessage.eml")),
            mailboxSession).getId();

        mailboxManager.moveMessages(MessageRange.all(), BOB_INBOX_PATH, BOB_OTHER_BOX_PATH, mailboxSession);

        assertThat(Mono.from(messageFastViewProjection.retrieve(composedId.getMessageId())).block())
            .isNotNull();
    }

    @Test
    void shouldKeepPreviewWhenMessageIdReferenceInCopied() throws Exception {
        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("fullMessage.eml")),
            mailboxSession).getId();

        mailboxManager.copyMessages(MessageRange.all(), BOB_INBOX_PATH, BOB_OTHER_BOX_PATH, mailboxSession);
        assertThat(Mono.from(messageFastViewProjection.retrieve(composedId.getMessageId())).block())
            .isNotNull();

        inboxMessageManager.delete(ImmutableList.of(composedId.getUid()), mailboxSession);

        assertThat(Mono.from(messageFastViewProjection.retrieve(composedId.getMessageId())).block())
            .isNotNull();
    }

    private Message previewMessage() throws Exception {
        return Message.Builder.of()
            .setSubject("Preview message")
            .setBody(PREVIEW.getValue(), StandardCharsets.UTF_8)
            .build();
    }

    private Message emptyMessage() throws Exception {
        return Message.Builder.of()
            .setSubject("Empty message")
            .setBody("", StandardCharsets.UTF_8)
            .build();
    }
}
