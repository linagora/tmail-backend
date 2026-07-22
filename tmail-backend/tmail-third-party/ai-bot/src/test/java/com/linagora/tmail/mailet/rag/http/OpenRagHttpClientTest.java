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

package com.linagora.tmail.mailet.rag.http;

import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.extension.WireMockAiServerExtension;
import com.linagora.tmail.mailet.rag.RagConfig;
import com.linagora.tmail.mailet.rag.httpclient.DocumentId;
import com.linagora.tmail.mailet.rag.httpclient.OpenRagHttpClient;
import com.linagora.tmail.mailet.rag.httpclient.OpenRagUnexpectedException;
import com.linagora.tmail.mailet.rag.httpclient.Partition;

public class OpenRagHttpClientTest {
    private static final String CHAT_COMPLETIONS_ENDPOINT = "/v1/chat/completions";

    @RegisterExtension
    static WireMockAiServerExtension wireMockRagServerExtension = new WireMockAiServerExtension();

    private OpenRagHttpClient client;

    @BeforeEach
    void setUp() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("openrag.url", wireMockRagServerExtension.getBaseUrl().toString());
        configuration.addProperty("openrag.token", "dummy-token");
        configuration.addProperty("openrag.ssl.trust.all.certs", "true");
        configuration.addProperty("openrag.partition.pattern", "{localPart}.twake.{domainName}");
        RagConfig ragConfig = RagConfig.from(configuration);
        client = new OpenRagHttpClient(ragConfig);
    }

    @Test
    void deleteDocumentShouldCallDeleteEndpoint() {
        wireMockRagServerExtension.setRagIndexerDeleteResponse(204, "");

        Partition partition = new Partition("bob.twake.example.com");
        DocumentId documentId = new DocumentId(TestMessageId.of(1));

        client.deleteDocument(partition, documentId).block();

        verify(1, deleteRequestedFor(urlMatching("/indexer/partition/.*/file/.*")));
    }

    @Test
    void deleteDocumentShouldFailOnRagServerError() {
        wireMockRagServerExtension.setRagIndexerDeleteResponse(500, "Internal error");

        Partition partition = new Partition("bob.twake.example.com");
        DocumentId documentId = new DocumentId(TestMessageId.of(1));

        assertThatThrownBy(() -> client.deleteDocument(partition, documentId).block())
            .isInstanceOf(OpenRagUnexpectedException.class);
    }
}
