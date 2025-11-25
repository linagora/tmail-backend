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

package com.linagora.tmail.mailbox.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.UUID;

import org.apache.james.backends.opensearch.DockerOpenSearchExtension;
import org.apache.james.backends.opensearch.IndexName;
import org.apache.james.backends.opensearch.OpenSearchIndexer;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.backends.opensearch.ReadAliasName;
import org.apache.james.backends.opensearch.WriteAliasName;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.opensearch.DefaultMailboxMappingFactory;
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
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.apache.james.mailbox.tika.TikaExtension;
import org.apache.james.mailbox.tika.TikaHttpClientImpl;
import org.apache.james.mailbox.tika.TikaTextExtractor;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.stream.RawField;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;

class TMailOpenSearchQueryStringTest {
    static final int SEARCH_SIZE = 10;
    private static final Username USERNAME = Username.of("user");
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @RegisterExtension
    static TikaExtension tika = new TikaExtension();

    @RegisterExtension
    static DockerOpenSearchExtension openSearch = new DockerOpenSearchExtension(DockerOpenSearchExtension.CleanupStrategy.NONE);

    TikaTextExtractor textExtractor;
    ReactorOpenSearchClient client;
    private InMemoryMailboxManager storeMailboxManager;
    private IndexName indexName;
    protected MailboxSession session;
    private MailboxPath inboxPath;


    @BeforeEach
    void setUp() throws Exception {
        textExtractor = new TikaTextExtractor(new RecordingMetricFactory(),
            new TikaHttpClientImpl(TikaConfiguration.builder()
                .host(tika.getIp())
                .port(tika.getPort())
                .timeoutInMillis(tika.getTimeoutInMillis())
                .build()));
        client = openSearch.getDockerOpenSearch().clientProvider().get();

        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();
        MailboxIdRoutingKeyFactory routingKeyFactory = new MailboxIdRoutingKeyFactory();
        ReadAliasName readAliasName = new ReadAliasName(UUID.randomUUID().toString());
        WriteAliasName writeAliasName = new WriteAliasName(UUID.randomUUID().toString());
        indexName = new IndexName(UUID.randomUUID().toString());
        MailboxIndexCreationUtil.prepareClient(client, readAliasName, writeAliasName, indexName,
            openSearch.getDockerOpenSearch().configuration(), new DefaultMailboxMappingFactory());

        OpenSearchMailboxConfiguration openSearchMailboxConfiguration = OpenSearchMailboxConfiguration.builder()
            .indexBody(IndexBody.YES)
            .useQueryStringQuery(true)
            .build();

        TmailOpenSearchMailboxConfiguration tmailOpenSearchMailboxConfiguration = TmailOpenSearchMailboxConfiguration.builder()
            .subjectNgramEnabled(true)
            .subjectNgramHeuristicEnabled(true)
            .attachmentFilenameNgramEnabled(true)
            .attachmentFilenameNgramHeuristicEnabled(true)
            .build();

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .listeningSearchIndex(preInstanciationStage -> new OpenSearchListeningMessageSearchIndex(
                preInstanciationStage.getMapperFactory(),
                ImmutableSet.of(),
                new OpenSearchIndexer(client, writeAliasName),
                new OpenSearchSearcher(client, new QueryConverter(new TmailCriterionConverter(openSearchMailboxConfiguration, tmailOpenSearchMailboxConfiguration)), SEARCH_SIZE, readAliasName, routingKeyFactory),
                new MessageToOpenSearchJson(textExtractor, ZoneId.of("Europe/Paris"), IndexAttachments.YES, IndexHeaders.YES, IndexBody.YES),
                preInstanciationStage.getSessionProvider(), routingKeyFactory, messageIdFactory,
                openSearchMailboxConfiguration, new RecordingMetricFactory(),
                ImmutableSet.of()))
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        storeMailboxManager = resources.getMailboxManager();

        session = storeMailboxManager.createSystemSession(USERNAME);
        inboxPath = MailboxPath.inbox(USERNAME);
        storeMailboxManager.createMailbox(inboxPath, session);
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Test
    void addressShouldBeSearchableWhenHavingHyphenCharacter() throws Exception {
        MessageManager messageManager = storeMailboxManager.getMailbox(inboxPath, session);

        Message.Builder messageBuilder = Message.Builder
            .of()
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8);

        ComposedMessageId messageId1 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                messageBuilder
                    .addField(new RawField("To", "test-user@domain-test.fr"))
                    .build()),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 1);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "test-user@domain-test.fr")), session)).toStream())
            .containsOnly(messageId1.getUid());
        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "test-user")), session)).toStream())
            .containsOnly(messageId1.getUid());
        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "domain-test")), session)).toStream())
            .containsOnly(messageId1.getUid());
    }

    private void awaitForOpenSearch(Query query, long totalHits) {
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(client.search(
                    new SearchRequest.Builder()
                        .index(indexName.getValue())
                        .query(query)
                        .build())
                .block()
                .hits().total().value()).isEqualTo(totalHits));
    }
}
