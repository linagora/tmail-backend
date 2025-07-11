package com.linagora.tmail.mailet.rag.httpclient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.linagora.tmail.mailet.rag.RagConfig;

import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

public class RagondinHttpClientTest {
    private WireMockServer wireMockServer;
    private RagondinHttpClient ragondinHttpClient;
    private RagConfig ragConfig;
    private  DocumentId documentId;
    private Partition partition;
    @BeforeEach
        public void setUp() throws Exception {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        ragConfig = new RagConfig("fake-token", true, Optional.of(URI.create(wireMockServer.baseUrl()).toURL()), "{localPart}.twake.{domainName}");

        ragondinHttpClient = new RagondinHttpClient(ragConfig);
        documentId = new DocumentId(new ThreadId(TestMessageId.of(123)));
        partition = Partition.fromPattern(ragConfig.getPartitionPattern(), "testPartition", "testDomain");
    }

    @AfterEach
    void tearDown() {wireMockServer.stop();}

    @Test
    void addDocument_shouldSucceed_whenServerReturns200() throws JsonProcessingException {
        String url = String.format("/indexer/partition/%s/file/%s",
            partition.partitionName(),
            documentId.asString());

        wireMockServer.stubFor(post(urlEqualTo(url))
            .willReturn(aResponse()
                .withStatus(200)));
        StepVerifier.create(ragondinHttpClient.addDocument(
                partition,
                documentId,
                "test content for the email that should  be indexed",
                Map.of("key", "value")))
            .verifyComplete();

        wireMockServer.verify(postRequestedFor(urlPathMatching(url))
            .withRequestBody(containing("key")));
    }
}
