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
 *******************************************************************/

package com.linagora.tmail.mailet.rag;

import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.extension.WireMockRagServerExtension;
import com.linagora.tmail.mailet.rag.httpclient.OpenRagHttpClient;
import com.linagora.tmail.mailet.rag.httpclient.Partition;

public class RagDeletionListenerTest {

    @RegisterExtension
    static WireMockRagServerExtension wireMockRagServerExtension = new WireMockRagServerExtension();

    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        RetryBackoffConfiguration backoffConfiguration = RetryBackoffConfiguration.builder()
            .maxRetries(2)
            .firstBackoff(Duration.ofMillis(1))
            .jitterFactor(0.5)
            .build();
        eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()),
            backoffConfiguration, new MemoryEventDeadLetters());

        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("openrag.url", wireMockRagServerExtension.getBaseUrl().toString());
        configuration.addProperty("openrag.token", "dummy-token");
        configuration.addProperty("openrag.ssl.trust.all.certs", "true");
        configuration.addProperty("openrag.partition.pattern", "{localPart}.twake.{domainName}");
        RagConfig ragConfig = RagConfig.from(configuration);
        OpenRagHttpClient openRagHttpClient = new OpenRagHttpClient(ragConfig);
        Partition.Factory partitionFactory = Partition.Factory.fromPattern(ragConfig.getPartitionPattern());

        RagDeletionListener ragDeletionListener = new RagDeletionListener(partitionFactory, openRagHttpClient);
        eventBus.register(ragDeletionListener);
    }

    @Test
    void shouldDeleteRagDocumentOnMessageContentDeletionEvent() {
        wireMockRagServerExtension.setRagIndexerDeleteResponse(204, "");

        Username username = Username.of("bob@domain.tld");
        MailboxEvents.MessageContentDeletionEvent messageContentDeletionEvent = new MailboxEvents.MessageContentDeletionEvent(
            Event.EventId.random(),
            username,
            InMemoryId.of(1),
            TestMessageId.of(1),
            123L,
            Instant.now(),
            false,
            "headerBlobId",
            "bodyBlobId");

        eventBus.dispatch(messageContentDeletionEvent, Set.of()).block();

        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> verify(1, deleteRequestedFor(urlMatching("/indexer/partition/.*/file/.*"))));
    }
}
