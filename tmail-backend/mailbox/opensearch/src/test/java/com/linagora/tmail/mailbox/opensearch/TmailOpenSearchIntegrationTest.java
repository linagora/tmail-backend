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
 * This class is copied & adapted from {@link org.apache.james.mailbox.opensearch.OpenSearchIntegrationTest}
 */

package com.linagora.tmail.mailbox.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.james.backends.opensearch.DockerOpenSearchExtension;
import org.apache.james.backends.opensearch.IndexName;
import org.apache.james.backends.opensearch.OpenSearchConfiguration;
import org.apache.james.backends.opensearch.OpenSearchIndexer;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.backends.opensearch.ReadAliasName;
import org.apache.james.backends.opensearch.WriteAliasName;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchOptions;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.opensearch.IndexAttachments;
import org.apache.james.mailbox.opensearch.IndexHeaders;
import org.apache.james.mailbox.opensearch.MailboxIdRoutingKeyFactory;
import org.apache.james.mailbox.opensearch.MailboxIndexCreationUtil;
import org.apache.james.mailbox.opensearch.OpenSearchMailboxConfiguration;
import org.apache.james.mailbox.opensearch.events.OpenSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.opensearch.json.MessageToOpenSearchJson;
import org.apache.james.mailbox.opensearch.query.QueryConverter;
import org.apache.james.mailbox.opensearch.search.OpenSearchSearcher;
import org.apache.james.mailbox.store.search.AbstractMessageSearchIndexTest;
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.apache.james.mailbox.tika.TikaExtension;
import org.apache.james.mailbox.tika.TikaHttpClientImpl;
import org.apache.james.mailbox.tika.TikaTextExtractor;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.stream.RawField;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.streams.Limit;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;

public class TmailOpenSearchIntegrationTest extends AbstractMessageSearchIndexTest {
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();
    static final int SEARCH_SIZE = 1;
    private final QueryConverter queryConverter = new QueryConverter(new TmailCriterionConverter(openSearchMailboxConfiguration(), tmailOpenSearchMailboxConfiguration()));

    @RegisterExtension
    static TikaExtension tika = new TikaExtension();

    @RegisterExtension
    static DockerOpenSearchExtension openSearch = new DockerOpenSearchExtension(DockerOpenSearchExtension.CleanupStrategy.NONE);

    static TikaTextExtractor textExtractor;
    static ReactorOpenSearchClient client;
    private ReadAliasName readAliasName;
    private WriteAliasName writeAliasName;
    private IndexName indexName;

    @BeforeAll
    static void setUpAll() throws Exception {
        client = openSearch.getDockerOpenSearch().clientProvider().get();
        textExtractor = new TikaTextExtractor(new RecordingMetricFactory(),
            new TikaHttpClientImpl(TikaConfiguration.builder()
                .host(tika.getIp())
                .port(tika.getPort())
                .timeoutInMillis(tika.getTimeoutInMillis())
                .build()));
    }

    @AfterAll
    static void tearDown() throws IOException {
        client.close();
    }

    @Override
    protected void awaitMessageCount(List<MailboxId> mailboxIds, SearchQuery query, long messageCount) {
        awaitForOpenSearch(queryConverter.from(mailboxIds, query), messageCount);
    }

    protected OpenSearchMailboxConfiguration openSearchMailboxConfiguration() {
        return OpenSearchMailboxConfiguration.builder()
            .optimiseMoves(false)
            .textFuzzinessSearch(false)
            .build();
    }

    private TmailOpenSearchMailboxConfiguration tmailOpenSearchMailboxConfiguration() {
        return TmailOpenSearchMailboxConfiguration.builder()
            .subjectNgramEnabled(true)
            .subjectNgramHeuristicEnabled(true)
            .attachmentFilenameNgramEnabled(true)
            .attachmentFilenameNgramHeuristicEnabled(true)
            .build();
    }

    @Override
    protected void initializeMailboxManager() {
        messageIdFactory = new InMemoryMessageId.Factory();

        MailboxIdRoutingKeyFactory routingKeyFactory = new MailboxIdRoutingKeyFactory();

        readAliasName = new ReadAliasName(UUID.randomUUID().toString());
        writeAliasName = new WriteAliasName(UUID.randomUUID().toString());
        indexName = new IndexName(UUID.randomUUID().toString());
        OpenSearchConfiguration openSearchConfiguration = openSearch.getDockerOpenSearch().configuration();
        MailboxIndexCreationUtil.prepareClient(
            client, readAliasName, writeAliasName, indexName,
            openSearchConfiguration,
            new TmailMailboxMappingFactory(openSearchConfiguration, tmailOpenSearchMailboxConfiguration()));

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .listeningSearchIndex(preInstanciationStage -> new OpenSearchListeningMessageSearchIndex(
                preInstanciationStage.getMapperFactory(),
                ImmutableSet.of(),
                new OpenSearchIndexer(client,
                    writeAliasName),
                new OpenSearchSearcher(client, queryConverter, SEARCH_SIZE,
                    readAliasName, routingKeyFactory),
                new MessageToOpenSearchJson(textExtractor, ZoneId.of("Europe/Paris"), IndexAttachments.YES, IndexHeaders.YES),
                preInstanciationStage.getSessionProvider(), routingKeyFactory, messageIdFactory,
                openSearchMailboxConfiguration(), new RecordingMetricFactory(),
                ImmutableSet.of()))
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        storeMailboxManager = resources.getMailboxManager();
        messageIdManager = resources.getMessageIdManager();
        messageSearchIndex = resources.getSearchIndex();
        eventBus = resources.getEventBus();
    }

    @Override
    protected MessageId initNewBasedMessageId() {
        return InMemoryMessageId.of(100);
    }

    @Override
    protected MessageId initOtherBasedMessageId() {
        return InMemoryMessageId.of(1000);
    }

    @Test
    void exactSubjectShouldMatchWhenReply() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId messageId1 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                Message.Builder
                    .of()
                    .setSubject("Re: Vieux téléphone?")
                    .setBody("benwa@apache.org email address do not exist", StandardCharsets.UTF_8)
                    .build()),
            session).getId();

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 14);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.subject("Re: Vieux téléphone?")), session)).toStream())
            .containsOnly(messageId1.getUid());
    }

    @Test
    void theDocumentShouldBeReindexWithNewMailboxWhenMoveMessages() throws Exception {
        // Given mailboxA, mailboxB. Add message in mailboxA
        MailboxPath mailboxA = MailboxPath.forUser(USERNAME, "mailboxA");
        MailboxPath mailboxB = MailboxPath.forUser(USERNAME, "mailboxB");
        MailboxId mailboxAId = storeMailboxManager.createMailbox(mailboxA, session).get();
        MailboxId mailboxBId = storeMailboxManager.createMailbox(mailboxB, session).get();

        ComposedMessageId composedMessageId = storeMailboxManager.getMailbox(mailboxAId, session)
            .appendMessage(MessageManager.AppendCommand.from(
                    Message.Builder.of()
                        .setTo("benwa@linagora.com")
                        .setBody(Strings.repeat("append to inbox A", 5000), StandardCharsets.UTF_8)),
                session).getId();

        awaitUntilAsserted(mailboxAId, 1);

        // When moving the message from mailboxA to mailboxB
        storeMailboxManager.moveMessages(MessageRange.from(composedMessageId.getUid()), mailboxAId, mailboxBId, session);

        // Then the message is not anymore when searching with mailboxA, but is in mailboxB
        awaitUntilAsserted(mailboxAId, 0);

        // verify the messageDocumentWasUpdated
        MessageUid bMessageUid = Flux.from(storeMailboxManager.getMailbox(mailboxBId, session).search(SearchQuery.matchAll(), session))
            .next().block();

        ObjectNode updatedDocument = client.get(
                new GetRequest.Builder()
                    .index(indexName.getValue())
                    .id(mailboxBId.serialize() + ":" + bMessageUid.asLong())
                    .routing(mailboxBId.serialize())
                    .build())
            .filter(GetResponse::found)
            .map(GetResponse::source)
            .block();

        assertThat(updatedDocument).isNotNull();
        assertSoftly(softly -> {
            softly.assertThat(updatedDocument.get("mailboxId").asText()).isEqualTo(mailboxBId.serialize());
            softly.assertThat(updatedDocument.get("uid").asLong()).isEqualTo(bMessageUid.asLong());
        });
    }

    @Test
    void theMessageShouldBeIndexedWhenMoveMessagesButIndexedDocumentNotFound() throws Exception {
        // Given mailboxA, mailboxB. Add message in mailboxA
        MailboxPath mailboxA = MailboxPath.forUser(USERNAME, "mailboxA");
        MailboxPath mailboxB = MailboxPath.forUser(USERNAME, "mailboxB");
        MailboxId mailboxAId = storeMailboxManager.createMailbox(mailboxA, session).get();
        MailboxId mailboxBId = storeMailboxManager.createMailbox(mailboxB, session).get();

        ComposedMessageId composedMessageId = storeMailboxManager.getMailbox(mailboxAId, session)
            .appendMessage(MessageManager.AppendCommand.from(
                    Message.Builder.of()
                        .setTo("benwa@linagora.com")
                        .setBody(Strings.repeat("append to inbox A", 5000), StandardCharsets.UTF_8)),
                session).getId();

        awaitUntilAsserted(mailboxAId, 1);

        // Try to delete the document manually to simulate a not found document.
        client.deleteByQuery(new DeleteByQueryRequest.Builder()
                .index(indexName.getValue())
                .query(new MatchAllQuery.Builder().build().toQuery())
                .build())
            .block();
        awaitUntilAsserted(mailboxAId, 0);

        // When moving the message from mailboxA to mailboxB
        storeMailboxManager.moveMessages(MessageRange.from(composedMessageId.getUid()), mailboxAId, mailboxBId, session);

        // Then the message should be indexed in mailboxB
        awaitUntilAsserted(mailboxBId, 1);
    }

    @Test
    void termsBetweenOpenSearchAndLuceneLimitDueTuNonAsciiCharsShouldBeTruncated() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo(recipient)
                    .setBody(Strings.repeat("0à2345678é", 3200), StandardCharsets.UTF_8)),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 14);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, recipient)), session)).toStream())
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    void tooLongTermsShouldNotMakeIndexingFail() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo(recipient)
                    .setBody(Strings.repeat("0123456789", 3300), StandardCharsets.UTF_8)),
            session).getId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(client.search(
                    new SearchRequest.Builder()
                        .index(indexName.getValue())
                        .query(QueryBuilders.matchAll().build().toQuery())
                        .build())
                .block()
                .hits().total().value()).isEqualTo(14));

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, recipient)), session)).toStream())
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    void fieldsExceedingLuceneLimitShouldNotBeIgnored() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo(recipient)
                    .setBody(Strings.repeat("0123456789 ", 5000), StandardCharsets.UTF_8)),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 14);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.bodyContains("0123456789")), session)).toStream())
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    void fieldsWithTooLongTermShouldStillBeIndexed() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo(recipient)
                    .setBody(Strings.repeat("0123456789 ", 5000) + " matchMe", StandardCharsets.UTF_8)),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 14);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.bodyContains("matchMe")), session)).toStream())
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    void reasonableLongTermShouldNotBeIgnored() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        String reasonableLongTerm = "dichlorodiphényltrichloroéthane";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo(recipient)
                    .setBody(reasonableLongTerm, StandardCharsets.UTF_8)),
            session).getId();

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 14);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.bodyContains(reasonableLongTerm)), session)).toStream())
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    void headerSearchShouldIncludeMessageWhenDifferentTypesOnAnIndexedField() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId customDateHeaderMessageId = messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mailCustomDateHeader.eml")),
            session).getId();

        ComposedMessageId customStringHeaderMessageId = messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mailCustomStringHeader.eml")),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 15);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.headerExists("Custom-header")), session)).toStream())
            .containsExactly(customDateHeaderMessageId.getUid(), customStringHeaderMessageId.getUid());
    }

    @Test
    void messageShouldStillBeIndexedEvenAfterOneFieldFailsIndexation() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mailCustomDateHeader.eml")),
            session);

        ComposedMessageId customStringHeaderMessageId = messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mailCustomStringHeader.eml")),
            session).getId();

        openSearch.awaitForOpenSearch();

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.all()), session)).toStream())
            .contains(customStringHeaderMessageId.getUid());
    }

    @Test
    void addressMatchesShouldBeExact() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        Message.Builder messageBuilder = Message.Builder
            .of()
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8);

        ComposedMessageId messageId1 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                messageBuilder
                    .addField(new RawField("To", "alice@domain.tld"))
                    .build()),
            session).getId();

        ComposedMessageId messageId2 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                messageBuilder
                    .addField(new RawField("To", "bob@other.tld"))
                    .build()),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 15);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "bob@other.tld")), session)).toStream())
            .containsOnly(messageId2.getUid());
    }

    @Test
    void addressMatchesShouldMatchDomainPart() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        Message.Builder messageBuilder = Message.Builder
            .of()
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8);

        ComposedMessageId messageId1 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                messageBuilder
                    .addField(new RawField("To", "alice@domain.tld"))
                    .build()),
            session).getId();

        ComposedMessageId messageId2 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                messageBuilder
                    .addField(new RawField("To", "bob@other.tld"))
                    .build()),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 15);
        Thread.sleep(500);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "other")), session)).toStream())
            .containsOnly(messageId2.getUid());
    }

    @Test
    void multiMailboxSearchShouldBeSupportedWhenUsersHaveManyMailboxes() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        Message.Builder messageBuilder = Message.Builder
            .of()
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8);

        ComposedMessageId messageId1 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                messageBuilder
                    .addField(new RawField("To", "alice@domain.tld"))
                    .build()),
            session).getId();

        ComposedMessageId messageId2 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                messageBuilder
                    .addField(new RawField("To", "bob@other.tld"))
                    .build()),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 15);
        Thread.sleep(500);

        Flux.range(0, 1050)
            .concatMap(i -> storeMailboxManager.createMailboxReactive(MailboxPath.forUser(USERNAME, "box" + i), session))
            .blockLast();

        MultimailboxesSearchQuery query = MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "other"))).build();
        assertThat(Flux.from(storeMailboxManager.search(query, session, SearchOptions.limit(Limit.limit(10)))).collectList().block())
            .containsOnly(messageId2.getMessageId());
    }

    @Test
    void shouldMatchFileExtension() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                Message.Builder
                    .of()
                    .setSubject("test")
                    .setBody("testmail", StandardCharsets.UTF_8)
                    .addField(new RawField("To", "alice@domain.tld"))
                    .build()),
            session).getId();

        ComposedMessageId messageId2 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/attachments-filename-in-content-type.eml")),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 15);
        Thread.sleep(500);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.mailContains("txt")), session)).toStream())
            .containsOnly(messageId2.getUid());
    }

    @Disabled("MAILBOX-403 Relaxed the matching constraints for email addresses in text bodies to reduce OpenSearch disk space usage")
    @Test
    public void textShouldNotMatchOtherAddressesOfTheSameDomain() {

    }

    @Test
    void localPartShouldBeMatchedWhenHyphen() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId messageId1 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                Message.Builder
                    .of()
                    .setSubject("test")
                    .setBody("testmail", StandardCharsets.UTF_8)
                    .addField(new RawField("To", "alice-test@domain.tld"))
                    .build()),
            session).getId();

        ComposedMessageId messageId2 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                Message.Builder
                    .of()
                    .setSubject("test")
                    .setBody("testmail", StandardCharsets.UTF_8)
                    .addField(new RawField("To", "bob@other.tld"))
                    .build()),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 15);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "alice-test")), session)).toStream())
            .containsOnly(messageId1.getUid());
    }

    @Test
    void addressShouldBeMatchedWhenHyphen() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId messageId1 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                Message.Builder
                    .of()
                    .setSubject("test")
                    .setBody("testmail", StandardCharsets.UTF_8)
                    .addField(new RawField("To", "alice-test@domain.tld"))
                    .build()),
            session).getId();

        ComposedMessageId messageId2 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                Message.Builder
                    .of()
                    .setSubject("test")
                    .setBody("testmail", StandardCharsets.UTF_8)
                    .addField(new RawField("To", "bob@other.tld"))
                    .build()),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 15);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "alice-test@domain.tld")), session)).toStream())
            .containsOnly(messageId1.getUid());
        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "alice-test")), session)).toStream())
            .containsOnly(messageId1.getUid());
        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "alice")), session)).toStream())
            .containsOnly(messageId1.getUid());
    }

    @Test
    void addressShouldBeMatchedOnSubLocalParts() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId messageId1 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                Message.Builder
                    .of()
                    .setSubject("test")
                    .setBody("testmail", StandardCharsets.UTF_8)
                    .addField(new RawField("To", "alice.test@domain.tld"))
                    .build()),
            session).getId();

        ComposedMessageId messageId2 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                Message.Builder
                    .of()
                    .setSubject("test")
                    .setBody("testmail", StandardCharsets.UTF_8)
                    .addField(new RawField("To", "bob@other.tld"))
                    .build()),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 15);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "alice.test@domain.tld")), session)).toStream())
            .containsOnly(messageId1.getUid());

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "alice.test")), session)).toStream())
            .containsOnly(messageId1.getUid());
    }

    @Test
    void searchDomainInSubject() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId messageId1 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                Message.Builder
                    .of()
                    .setBody("testmail", StandardCharsets.UTF_8)
                    .setSubject("Renew SSL certificate linagora.com")
                    .build()),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 14);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.subject("certificate")), session)).toStream())
            .containsOnly(messageId1.getUid());
        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.subject("Renew")), session)).toStream())
            .containsOnly(messageId1.getUid());
        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.subject("linagora.com")), session)).toStream())
            .containsOnly(messageId1.getUid());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "example.com",
        "nas-backup.example.com",
        "[nas-backup.example.com]",
        "nas",
        "backup",
    })
    void mailingListPrefixShouldBePreservedInSearch(String subject) throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId messageId1 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                Message.Builder
                    .of()
                    .setBody("testmail", StandardCharsets.UTF_8)
                    .setSubject("[nas-backup.example.com] Backup completed successfully")
                    .build()),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 14);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.subject(subject)), session)).toStream())
            .containsOnly(messageId1.getUid());
    }

    @Test
    void domainPartShouldBeMatchedWhenHyphen() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId messageId1 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                Message.Builder
                    .of()
                    .setSubject("test")
                    .setBody("testmail", StandardCharsets.UTF_8)
                    .addField(new RawField("To", "alice@domain-test.tld"))
                    .build()),
            session).getId();

        ComposedMessageId messageId2 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                Message.Builder
                    .of()
                    .setSubject("test")
                    .setBody("testmail", StandardCharsets.UTF_8)
                    .addField(new RawField("To", "bob@other.tld"))
                    .build()),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 15);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "domain-test.tld")), session)).toStream())
            .containsOnly(messageId1.getUid());
        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "domain-test")), session)).toStream())
            .containsOnly(messageId1.getUid());
    }

    @Test
    void shouldSortOnBaseSubject() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, "def");
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        storeMailboxManager.createMailbox(mailboxPath, session);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId messageId1 = messageManager.appendMessage(messageWithSubject("abc"), session).getId();
        ComposedMessageId messageId2 = messageManager.appendMessage(messageWithSubject("Re: abd"), session).getId();
        ComposedMessageId messageId3 = messageManager.appendMessage(messageWithSubject("Fwd: abe"), session).getId();
        ComposedMessageId messageId4 = messageManager.appendMessage(messageWithSubject("bbc"), session).getId();
        ComposedMessageId messageId5 = messageManager.appendMessage(messageWithSubject("bBc"), session).getId();
        ComposedMessageId messageId6 = messageManager.appendMessage(messageWithSubject("def"), session).getId();
        ComposedMessageId messageId7 = messageManager.appendMessage(messageWithSubject("ABC"), session).getId();

        openSearch.awaitForOpenSearch();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 20);

        assertThat(Flux.from(
            messageManager.search(SearchQuery.allSortedWith(new SearchQuery.Sort(SearchQuery.Sort.SortClause.BaseSubject)), session)).toStream())
            .containsExactly(messageId1.getUid(),
                messageId7.getUid(),
                messageId2.getUid(),
                messageId3.getUid(),
                messageId4.getUid(),
                messageId5.getUid(),
                messageId6.getUid());
    }

    @Test
    void subjectWithSpaceShouldBePartiallySearchableWhenNgramEnabled() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId messageId = messageManager.appendMessage(messageWithSubject("abc def ghi"), session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 14);
        Thread.sleep(500);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.subject("ef gh")), session)).toStream())
            .containsOnly(messageId.getUid());
    }

    @Test
    void subjectWithSpaceShouldNotBePartiallySearchableWhenNgramHeuristicEnabledAndSearchHigherThan6Characters() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        messageManager.appendMessage(messageWithSubject("abc def ghi"), session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 14);
        Thread.sleep(500);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.subject("c ef gh")), session)).toStream())
            .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "CV",
        "BoB",
        "2024",
        "CV_bob"
    })
    void subjectWithUnderscoreShouldSearchable(String searchInput) throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId messageId = messageManager.appendMessage(messageWithSubject("CV_Bob_2024"), session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 14);
        Thread.sleep(500);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.subject(searchInput)), session)).toStream())
            .contains(messageId.getUid());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Résum",
        "resum",
        "coör",
        "coor"
    })
    void subjectWithNonASCIICharactersShouldBeSearchAble(String searchInput) throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId messageId = messageManager.appendMessage(messageWithSubject("Résumé café naïve coördinate"), session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 14);
        Thread.sleep(500);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.subject(searchInput)), session)).toStream())
            .contains(messageId.getUid());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "big bad wolf 1.txt",
        "big bad wolf.txt",
        "bad wolf.txt",
        "wolf.txt",
        "1.txt",
        "big bad wolf",
        "big bad",
        "big",
        "bad",
        "wolf",
        "1",
        "txt",
    })
    void attachmentFilenameShouldBeSearchableByWords(String searchInput) throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId messageId = messageManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(emlWithAttachmentFilename("big_bad wolf-1.txt")),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 14);
        Thread.sleep(500);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.attachmentFileName(searchInput)), session)).toStream())
            .containsOnly(messageId.getUid());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "bi",
        "ig",
        "ba",
        "ad",
        "wo",
        "wol",
        "20",
        "202",
        "wol 20",
        "doc"
    })
    void attachmentFilenameShouldBeSearchableByPartialOfWords(String searchInput) throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId messageId = messageManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(emlWithAttachmentFilename("big_bad wolf-2025.docx")),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 14);
        Thread.sleep(500);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.attachmentFileName(searchInput)), session)).toStream())
            .contains(messageId.getUid());
    }

    private static MessageManager.AppendCommand messageWithSubject(String subject) throws IOException {
        return MessageManager.AppendCommand.builder().build(
            Message.Builder
                .of()
                .setBody("testmail", StandardCharsets.UTF_8)
                .addField(new RawField("Subject", subject)));
    }

    protected void awaitForOpenSearch(Query query, long totalHits) {
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(client.search(
                    new SearchRequest.Builder()
                        .index(indexName.getValue())
                        .query(query)
                        .build())
                .block()
                .hits().total().value()).isEqualTo(totalHits));
    }

    private void awaitUntilAsserted(MailboxId mailboxId, long expectedCountResult) {
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(messageSearchIndex.search(session, List.of(mailboxId), SearchQuery.matchAll(), SearchOptions.limit(Limit.limit(100))).toStream().count())
                .isEqualTo(expectedCountResult));
    }

    private String emlWithAttachmentFilename(String attachmentFilename) throws IOException {
        String template = ClassLoaderUtils.getSystemResourceAsString("eml/template/attachment-filename.eml.mustache");
        MustacheFactory mustacheFactory = new DefaultMustacheFactory();
        Mustache mustache = mustacheFactory.compile(new StringReader(template), "attachment-filename");
        Map<String, Object> params = new HashMap<>();
        params.put("attachmentFilename", attachmentFilename);
        StringWriter writer = new StringWriter();
        mustache.execute(writer, params).flush();
        return writer.toString();
    }
}
