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
package com.linagora.tmail.mailet.rag.httpclient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.linagora.tmail.mailet.rag.RagConfig;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class OpenRagHttpClientTest {
    private WireMockServer wireMockServer;
    private OpenRagHttpClient openRagHttpClient;
    private RagConfig ragConfig;
    private  DocumentId documentId;
    private Partition partition;
    ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    public void setUp() throws Exception {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        ragConfig = new RagConfig("fake-token", true, Optional.of(URI.create(wireMockServer.baseUrl()).toURL()), "{localPart}.twake.{domainName}");

        openRagHttpClient = new OpenRagHttpClient(ragConfig);
        documentId = new DocumentId(new ThreadId(TestMessageId.of(123)));
        partition = Partition.fromPattern(ragConfig.getPartitionPattern(), "testPartition", "testDomain");
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void addDocumentShouldFormCorrectHttpRequest() throws JsonProcessingException {
        String url = String.format("/indexer/partition/%s/file/%s",
            partition.partitionName(),
            documentId.asString());

        wireMockServer.stubFor(post(urlEqualTo(url))
            .willReturn(aResponse()
                .withStatus(200)));
        StepVerifier.create(openRagHttpClient.addDocument(
                partition,
                documentId,
                "test content for the email that should  be indexed",
                Map.of("key", "value")))
            .verifyComplete();

        wireMockServer.verify(postRequestedFor(urlPathMatching(url))
            .withHeader("Content-Type", containing("multipart/form-data"))
            .withRequestBody(containing("key")));
    }

    @Disabled("Disabled because the test requires a real authentificationToken to run")
    @Test
    void shouldReceiveRealServerResponsee() throws Exception {
        RagConfig ragConf = new RagConfig("fake", true, Optional.of(URI.create("https://ragondin-twake-staging.linagora.com/").toURL()), "{localPart}.twake.{domainName}");
        OpenRagHttpClient openRagHttpClient = new OpenRagHttpClient(ragConf);

        Mono<String> response = openRagHttpClient.addDocument(
                Partition.fromPattern("{localPart}.twake.{domainName}", "test", "linagora.com"),
                new DocumentId(new ThreadId(TestMessageId.of(9))),
                "Contenu du fichier RAG on Twake Mail",
                Map.of("link", "https://example.com",
                    "date", "2023-10-01"));

        assertThat(response.block()).matches("\\{\"task_status_url\":\"http://ragondin-twake-staging.linagora.com/indexer/task/.*\"}");
    }

}
