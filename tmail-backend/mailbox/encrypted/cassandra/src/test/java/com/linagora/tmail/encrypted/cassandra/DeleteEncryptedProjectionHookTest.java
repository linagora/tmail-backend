package com.linagora.tmail.encrypted.cassandra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.MetricableBlobStore;
import org.apache.james.blob.memory.MemoryBlobStoreFactory;
import org.apache.james.core.Username;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.CassandraTestSystemFixture;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.MailboxManagerConfiguration;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.MailboxAnnotationListener;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.NaiveThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.encrypted.EncryptedEmailContent;
import com.linagora.tmail.encrypted.EncryptedEmailContentStore;
import com.linagora.tmail.encrypted.MessageNotFoundException;
import com.linagora.tmail.encrypted.cassandra.table.CassandraEncryptedEmailStoreModule;

import reactor.core.publisher.Mono;
import scala.Option;
import scala.jdk.javaapi.CollectionConverters;

public class DeleteEncryptedProjectionHookTest {

    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();
    private static final Username USER = Username.of("user@domain.org");
    private static final MailboxPath INBOX = MailboxPath.inbox(USER);
    private static final MailboxPath ARCHIVE_MAILBOX = MailboxPath.forUser(USER, DefaultMailboxes.ARCHIVE);

    private EncryptedEmailContentStore encryptedEmailContentStore;
    private CassandraMailboxManager mailboxManager;

    private MailboxSession mailboxSession;
    private MailboxId inboxId;
    private MessageManager inboxMessageManager;

    static class MetricableBlobStoreExtension implements BeforeEachCallback {
        private RecordingMetricFactory metricFactory;

        @Override
        public void beforeEach(ExtensionContext extensionContext) {
            this.metricFactory = new RecordingMetricFactory();
        }

        public RecordingMetricFactory getMetricFactory() {
            return metricFactory;
        }
    }

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(
        CassandraEncryptedEmailStoreModule.MODULE(),
        MailboxAggregateModule.MODULE));

    @RegisterExtension
    MetricableBlobStoreExtension metricsTestExtension = new MetricableBlobStoreExtension();

    CassandraMailboxManager createMailboxManager(CassandraMailboxSessionMapperFactory mapperFactory) {
        Preconditions.checkNotNull(encryptedEmailContentStore);

        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        StoreRightManager storeRightManager = new StoreRightManager(mapperFactory, new UnionMailboxACLResolver(), new SimpleGroupMembershipResolver(), eventBus);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mapperFactory, storeRightManager);

        SessionProviderImpl sessionProvider = new SessionProviderImpl(mock(Authenticator.class), mock(Authorizator.class));

        QuotaComponents quotaComponents = QuotaComponents.disabled(sessionProvider, mapperFactory);
        AttachmentContentLoader attachmentContentLoader = null;
        MessageSearchIndex index = new SimpleMessageSearchIndex(mapperFactory, mapperFactory, new DefaultTextExtractor(), attachmentContentLoader);

        StoreMessageIdManager messageIdManager = new StoreMessageIdManager(
            storeRightManager,
            mapperFactory,
            eventBus,
            quotaComponents.getQuotaManager(),
            new DefaultUserQuotaRootResolver(sessionProvider, mapperFactory),
            PreDeletionHooks.NO_PRE_DELETION_HOOK);

        DeleteEncryptedProjectionHook preDeleteEncryptedProjectionHook = new DeleteEncryptedProjectionHook(
            encryptedEmailContentStore,
            sessionProvider,
            messageIdManager,
            mapperFactory);

        PreDeletionHooks preDeletionHooks = new PreDeletionHooks(ImmutableSet.of(preDeleteEncryptedProjectionHook),
            new RecordingMetricFactory());

        CassandraMailboxManager cassandraMailboxManager = new CassandraMailboxManager(mapperFactory, sessionProvider,
            new NoMailboxPathLocker(), new MessageParser(), new CassandraMessageId.Factory(),
            eventBus, annotationManager, storeRightManager, quotaComponents, index,
            MailboxManagerConfiguration.DEFAULT,
            preDeletionHooks, new NaiveThreadIdGuessingAlgorithm());

        eventBus.register(new MailboxAnnotationListener(mapperFactory, sessionProvider));
        eventBus.register(mapperFactory.deleteMessageListener());

        return cassandraMailboxManager;
    }

    @BeforeEach
    void setUp(CassandraCluster cassandra) throws Exception {
        BlobStore blobStore = new MetricableBlobStore(metricsTestExtension.getMetricFactory(), MemoryBlobStoreFactory.builder()
            .blobIdFactory(BLOB_ID_FACTORY)
            .defaultBucketName()
            .passthrough());

        encryptedEmailContentStore = new CassandraEncryptedEmailContentStore(blobStore, new CassandraEncryptedEmailDAO(cassandra.getConf(), BLOB_ID_FACTORY));

        CassandraMailboxSessionMapperFactory mapperFactory = CassandraTestSystemFixture
            .createMapperFactory(cassandraCluster.getCassandraCluster());
        mailboxManager = createMailboxManager(mapperFactory);

        mailboxSession = mailboxManager.createSystemSession(USER);
        inboxId = mailboxManager.createMailbox(INBOX, mailboxSession).orElseThrow();
        inboxMessageManager = mailboxManager.getMailbox(inboxId, mailboxSession);
    }

    @Test
    void shouldRemoveEntryWhenDeletingMessage() throws Exception {
        ComposedMessageId composedId = createNewMessage();
        inboxMessageManager.delete(ImmutableList.of(composedId.getUid()), mailboxSession);
        assertThatThrownBy(() -> Mono.from(encryptedEmailContentStore.retrieveFastView(composedId.getMessageId())).block())
            .isInstanceOf(MessageNotFoundException.class);
    }

    @Test
    void shouldRemoveEntriesWhenDeletingMultiMessages() throws Exception {
        ComposedMessageId composedId = createNewMessage();
        ComposedMessageId composedId2 = createNewMessage();

        inboxMessageManager.delete(ImmutableList.of(composedId.getUid(), composedId2.getUid()), mailboxSession);

        assertSoftly(softly -> {
            softly.assertThatThrownBy(() -> Mono.from(encryptedEmailContentStore.retrieveFastView(composedId.getMessageId())).block())
                .isInstanceOf(MessageNotFoundException.class);
            softly.assertThatThrownBy(() -> Mono.from(encryptedEmailContentStore.retrieveFastView(composedId2.getMessageId())).block())
                .isInstanceOf(MessageNotFoundException.class);
        });
    }

    @Test
    void shouldRemoveEntriesWhenMailboxDeletion() throws Exception {
        ComposedMessageId composedId = createNewMessage();
        ComposedMessageId composedId2 = createNewMessage();

        mailboxManager.deleteMailbox(inboxId, mailboxSession);

        assertSoftly(softly -> {
            softly.assertThatThrownBy(() -> Mono.from(encryptedEmailContentStore.retrieveFastView(composedId.getMessageId())).block())
                .isInstanceOf(MessageNotFoundException.class);
            softly.assertThatThrownBy(() -> Mono.from(encryptedEmailContentStore.retrieveFastView(composedId2.getMessageId())).block())
                .isInstanceOf(MessageNotFoundException.class);
        });
    }

    @Test
    void shouldNotRemoveEntriesInMailboxNotDeletion() throws Exception {
        mailboxManager.createMailbox(ARCHIVE_MAILBOX, mailboxSession);
        ComposedMessageId composedId = createNewMessage();

        mailboxManager.deleteMailbox(ARCHIVE_MAILBOX, mailboxSession);

        assertThat(Mono.from(encryptedEmailContentStore.retrieveFastView(composedId.getMessageId())).block())
            .isNotNull();
    }

    @Test
    void shouldRemoveExactlyEntryWhenDeletingMessage() throws Exception {
        ComposedMessageId composedId = createNewMessage();
        ComposedMessageId composedId2 = createNewMessage();

        inboxMessageManager.delete(ImmutableList.of(composedId.getUid()), mailboxSession);

        assertSoftly(softly -> {
            softly.assertThatThrownBy(() -> Mono.from(encryptedEmailContentStore.retrieveFastView(composedId.getMessageId())).block())
                .isInstanceOf(MessageNotFoundException.class);
            softly.assertThat(Mono.from(encryptedEmailContentStore.retrieveFastView(composedId2.getMessageId())).block())
                .isNotNull();
        });
    }

    @Test
    void shouldNotRemoveEntriesWhenMoveMessagesFromInboxToArchive() throws Exception {
        mailboxManager.createMailbox(ARCHIVE_MAILBOX, mailboxSession);

        ComposedMessageId composedMessageId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(newMessage()),
            mailboxSession).getId();

        EncryptedEmailContent encryptedEmailContent = new EncryptedEmailContent(
            "encryptedPreview1",
            "encryptedHtml2",
            true,
            Option.apply("encryptedAttachmentMetadata1"),
            CollectionConverters.asScala(List.of("encryptedAttachmentContents1")).toList());
        Mono.from(encryptedEmailContentStore.store(composedMessageId.getMessageId(), encryptedEmailContent)).block();

        mailboxManager.moveMessages(MessageRange.all(), INBOX, ARCHIVE_MAILBOX, mailboxSession);

        assertThat(Mono.from(encryptedEmailContentStore.retrieveFastView(composedMessageId.getMessageId())).block())
            .isNotNull();
    }

    @Test
    void shouldNotRemoveEntryWhenMessageStillAccessible() throws Exception {
        mailboxManager.createMailbox(ARCHIVE_MAILBOX, mailboxSession);

        ComposedMessageId composedMessageId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(newMessage()),
            mailboxSession).getId();
        MessageId messageId = composedMessageId.getMessageId();

        EncryptedEmailContent encryptedEmailContent = new EncryptedEmailContent(
            "encryptedPreview1",
            "encryptedHtml2",
            true,
            Option.apply("encryptedAttachmentMetadata1"),
            CollectionConverters.asScala(List.of("encryptedAttachmentContents1")).toList());

        Mono.from(encryptedEmailContentStore.store(messageId, encryptedEmailContent)).block();
        mailboxManager.copyMessages(MessageRange.all(), INBOX, ARCHIVE_MAILBOX, mailboxSession);
        inboxMessageManager.delete(ImmutableList.of(composedMessageId.getUid()), mailboxSession);


        assertThat(Mono.from(encryptedEmailContentStore.retrieveFastView(messageId)).block())
            .isNotNull();
    }

    @Test
    void shouldRemoveEntryWhenMessageDoesNotAccessible() throws Exception {
        mailboxManager.createMailbox(ARCHIVE_MAILBOX, mailboxSession);
        MessageManager archiveMessageManager = mailboxManager.getMailbox(ARCHIVE_MAILBOX, mailboxSession);

        ComposedMessageId composedMessageId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(newMessage()),
            mailboxSession).getId();
        MessageId messageId = composedMessageId.getMessageId();

        EncryptedEmailContent encryptedEmailContent = new EncryptedEmailContent(
            "encryptedPreview1",
            "encryptedHtml2",
            true,
            Option.apply("encryptedAttachmentMetadata1"),
            CollectionConverters.asScala(List.of("encryptedAttachmentContents1")).toList());

        Mono.from(encryptedEmailContentStore.store(messageId, encryptedEmailContent)).block();
        MessageUid archiveMessageUid = mailboxManager.copyMessages(MessageRange.all(), INBOX, ARCHIVE_MAILBOX, mailboxSession).get(0).getUidTo();

        inboxMessageManager.delete(ImmutableList.of(composedMessageId.getUid()), mailboxSession);
        archiveMessageManager.delete(ImmutableList.of(archiveMessageUid), mailboxSession);

        assertThatThrownBy(() -> Mono.from(encryptedEmailContentStore.retrieveFastView(messageId)).block())
            .isInstanceOf(MessageNotFoundException.class);
    }

    private Message newMessage() throws Exception {
        return Message.Builder.of()
            .setSubject("New message")
            .setBody("This is an awesome message body", StandardCharsets.UTF_8)
            .build();
    }

    private ComposedMessageId createNewMessage() throws Exception {
        ComposedMessageId composedMessageId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(newMessage()),
            mailboxSession).getId();

        EncryptedEmailContent encryptedEmailContent = new EncryptedEmailContent(
            "encryptedPreview1",
            "encryptedHtml2",
            true,
            Option.apply("encryptedAttachmentMetadata1"),
            CollectionConverters.asScala(List.of("encryptedAttachmentContents1")).toList());

        Mono.from(encryptedEmailContentStore.store(composedMessageId.getMessageId(), encryptedEmailContent)).block();
        return composedMessageId;
    }
}
