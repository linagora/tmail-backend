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

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.mail.util.SharedByteArrayInputStream;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;
import org.apache.commons.configuration2.io.FileHandler;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.SystemMailboxesProviderImpl;
import org.apache.james.mailbox.store.mail.NaiveThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableMap;
import com.linagora.tmail.extension.WireMockAiServerExtension;
import com.linagora.tmail.james.jmap.event.ApplyWhenFilter;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepositoryJavaUtils;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepository;
import com.linagora.tmail.mailet.rag.httpclient.OpenRagClient;
import com.linagora.tmail.mailet.rag.httpclient.Partition;
import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository;
import com.linagora.tmail.saas.filter.SaaSPayingUser;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

class SaaSUserRagListenerTest {
    private static final String RAG_INDEXER_ENDPOINT = "/indexer/partition/.*/file/.*";
    private static final Username BOB = Username.of("bob@test.com");
    private static final MailboxPath BOB_INBOX_PATH = MailboxPath.inbox(BOB);
    private static final byte[] CONTENT = (
        "Subject: Test Subject\r\n" +
            "From: sender@example.com\r\n" +
            "To: recipient@example.com\r\n" +
            "Cc: cc@example.com\r\n" +
            "Date: Tue, 10 Oct 2023 10:00:00 +0000\r\n" +
            "\r\n" +
            "Body of the email").getBytes(StandardCharsets.UTF_8);

    @RegisterExtension
    static WireMockAiServerExtension wireMockRagServerExtension = new WireMockAiServerExtension();

    StoreMailboxManager mailboxManager;
    RagListener ragListener;
    MessageIdManager messageIdManager;
    ThreadIdGuessingAlgorithm threadIdGuessingAlgorithm;
    SystemMailboxesProviderImpl systemMailboxesProvider;
    MailboxSession bobMailboxSession;
    MessageManager bobInboxMessageManager;
    MailboxId bobInboxId;
    JmapSettingsRepository jmapSettingsRepository;
    JmapSettingsRepositoryJavaUtils jmapSettingsRepositoryUtils;
    MemorySaaSAccountRepository saasAccountRepository;
    OpenRagClient openRagClient;
    Partition.Factory partitionFactory;

    @BeforeEach
    void setUp() throws Exception {
        wireMockRagServerExtension.setRagIndexerPostResponse(200,
            String.format("{\"task_status_url\":\"http://localhost:%d/status/1234\"}", wireMockRagServerExtension.getPort()));

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        threadIdGuessingAlgorithm = new NaiveThreadIdGuessingAlgorithm();
        mailboxManager = resources.getMailboxManager();
        messageIdManager = resources.getMessageIdManager();
        systemMailboxesProvider = new SystemMailboxesProviderImpl(mailboxManager);

        bobMailboxSession = mailboxManager.createSystemSession(BOB);
        bobInboxId = mailboxManager.createMailbox(BOB_INBOX_PATH, bobMailboxSession).get();
        bobInboxMessageManager = mailboxManager.getMailbox(bobInboxId, bobMailboxSession);

        PropertiesConfiguration config = new PropertiesConfiguration();
        config.addProperty("openrag.url", String.format("http://localhost:%d", wireMockRagServerExtension.getPort()));
        config.addProperty("openrag.token", "dummy-token");
        config.addProperty("openrag.ssl.trust.all.certs", "true");
        config.addProperty("openrag.partition.pattern", "{localPart}.twake.{domainName}");
        RagConfig ragConfig = RagConfig.from(config);
        openRagClient = new OpenRagClient(ragConfig);
        partitionFactory = Partition.Factory.fromPattern(ragConfig.getPartitionPattern());

        jmapSettingsRepository = new MemoryJmapSettingsRepository();
        jmapSettingsRepositoryUtils = new JmapSettingsRepositoryJavaUtils(jmapSettingsRepository);
        saasAccountRepository = new MemorySaaSAccountRepository();

        HierarchicalConfiguration<ImmutableNode> listenerConfig = loadRagListenerConfig();
        ApplyWhenFilter applyWhenFilter = new SaaSPayingUser(saasAccountRepository);

        ragListener = new RagListener(mailboxManager, messageIdManager, systemMailboxesProvider,
            threadIdGuessingAlgorithm, jmapSettingsRepository, partitionFactory, openRagClient, applyWhenFilter);

        jmapSettingsRepositoryUtils.reset(BOB, ImmutableMap.of("ai.rag.enabled", "true"));
    }

    @Test
    void payingUserShouldTriggerRagIndexing() throws Exception {
        assertRagIndexing(new SaaSAccount(true, true), 1);
    }

    @Test
    void nonPayingUserShouldNotTriggerRagIndexing() throws Exception {
        assertRagIndexing(new SaaSAccount(true, false), 0);
    }

    @Test
    void noSaaSAccountStoredShouldNotTriggerRagIndexing() throws Exception {
        assertRagIndexing(null, 0);
    }

    private void assertRagIndexing(SaaSAccount account, int expectedCallCount) throws Exception {
        mailboxManager.getEventBus().register(ragListener);

        if (account != null) {
            Mono.from(saasAccountRepository.upsertSaasAccount(BOB, account)).block();
        }

        bobInboxMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)),
            bobMailboxSession);

        verify(expectedCallCount, postRequestedFor(urlMatching(RAG_INDEXER_ENDPOINT)));
    }

    private HierarchicalConfiguration<ImmutableNode> loadRagListenerConfig() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("listeners.xml")) {
            FileBasedConfigurationBuilder<XMLConfiguration> builder = new FileBasedConfigurationBuilder<>(XMLConfiguration.class)
                .configure(new Parameters()
                    .xml()
                    .setListDelimiterHandler(new DisabledListDelimiterHandler()));
            XMLConfiguration xmlConfig = builder.getConfiguration();
            new FileHandler(xmlConfig).load(is);

            List<HierarchicalConfiguration<ImmutableNode>> listeners = xmlConfig.configurationsAt("listener");
            for (HierarchicalConfiguration<ImmutableNode> listener : listeners) {
                if (RagListener.class.getName().equals(listener.getString("class"))) {
                    return listener.configurationsAt("configuration")
                        .stream()
                        .findFirst()
                        .orElse(new BaseHierarchicalConfiguration());
                }
            }
        }
        throw new IllegalStateException("RagListener not found in listeners.xml");
    }
}