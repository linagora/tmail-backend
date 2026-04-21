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
package com.linagora.tmail.listener.rag.prompt;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class HttpPromptRetrieverTest {

    private WireMockServer wireMockServer;
    private HttpPromptRetriever httpPromptRetriever;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        configureFor("localhost", wireMockServer.port());

        httpPromptRetriever = new HttpPromptRetriever(); // uses HttpClient.create()
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldExtractSystemAndUserPromptsForGivenPromptName() throws Exception {
        String body = """
            {
              "generatedAt": "2026-04-16T07:53:55.268Z",
              "prompts": [
                {
                  "name": "classify-email",
                  "version": "1.0.0",
                  "description": "Classify email",
                  "messages": [
                    { "role": "system", "content": "SYSTEM_PROMPT_CONTENT" },
                    { "role": "user", "content": "USER_PROMPT_CONTENT {{input}}" }
                  ]
                }
              ]
            }
            """;

        wireMockServer.stubFor(get(urlEqualTo("/prompts/email/latest.json"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json; charset=utf-8")
                .withBody(body.getBytes(StandardCharsets.UTF_8))));

        URL url = new URL("http://localhost:" + wireMockServer.port() + "/prompts/email/latest.json");

        StepVerifier.create(httpPromptRetriever.retrievePrompts(url, "classify-email"))
            .assertNext(prompts -> {
                assertThat(prompts.system()).isEqualTo(Optional.of("SYSTEM_PROMPT_CONTENT"));
                assertThat(prompts.user()).isEqualTo(Optional.of("USER_PROMPT_CONTENT {{input}}"));
            })
            .verifyComplete();
    }

    @Test
    void shouldErrorWhenPromptNameNotFound() throws Exception {
        String body = """
            {
              "generatedAt": "2026-04-16T07:53:55.268Z",
              "prompts": [
                {
                  "name": "other",
                  "messages": [
                    { "role": "system", "content": "SYS" },
                    { "role": "user", "content": "USR" }
                  ]
                }
              ]
            }
            """;

        wireMockServer.stubFor(get(urlEqualTo("/prompts/email/latest.json"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json; charset=utf-8")
                .withBody(body)));

        URL url = new URL("http://localhost:" + wireMockServer.port() + "/prompts/email/latest.json");

        StepVerifier.create(httpPromptRetriever.retrievePrompts(url, "classify-email"))
            .expectErrorSatisfies(err -> {
                assertThat(err).isInstanceOf(PromptRetrievalException.class);
                assertThat(err.getMessage()).contains("Prompt 'classify-email' not found");
            })
            .verify();
    }

    @Test
    void shouldErrorWhenSystemPromptMissing() throws Exception {
        String body = """
            {
              "generatedAt": "2026-04-16T07:53:55.268Z",
              "prompts": [
                {
                  "name": "classify-email",
                  "messages": [
                    { "role": "user", "content": "USER_ONLY" }
                  ]
                }
              ]
            }
            """;

        wireMockServer.stubFor(get(urlEqualTo("/prompts/email/latest.json"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json; charset=utf-8")
                .withBody(body)));

        URL url = new URL("http://localhost:" + wireMockServer.port() + "/prompts/email/latest.json");

        StepVerifier.create(httpPromptRetriever.retrievePrompts(url, "classify-email"))
            .assertNext(prompts -> {
                assertThat(prompts.system()).isEmpty();
                assertThat(prompts.user()).contains("USER_ONLY");
            })
            .verifyComplete();
    }

    @Test
    void shouldErrorWhenHttpStatusNot200() throws Exception {
        wireMockServer.stubFor(get(urlEqualTo("/prompts/email/latest.json"))
            .willReturn(aResponse().withStatus(500)));

        URL url = new URL("http://localhost:" + wireMockServer.port() + "/prompts/email/latest.json");

        StepVerifier.create(httpPromptRetriever.retrievePrompts(url, "classify-email"))
            .expectErrorSatisfies(err -> {
                assertThat(err).isInstanceOf(PromptRetrievalException.class);
                assertThat(err.getMessage()).contains("Prompt download failed (500)");
            })
            .verify();
    }
}
