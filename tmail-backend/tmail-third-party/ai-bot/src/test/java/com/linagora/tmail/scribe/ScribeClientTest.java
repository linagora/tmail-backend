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
package com.linagora.tmail.scribe;

import java.nio.charset.StandardCharsets;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.extension.WireMockAiServerExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class ScribeClientTest {
    private static final String CHAT_COMPLETIONS_ENDPOINT = "/v1/chat/completions";

    @RegisterExtension
    static WireMockAiServerExtension wireMockRagServerExtension = new WireMockAiServerExtension();

    private ScribeClient client;

    @BeforeEach
    void setUp() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("scribe.url", wireMockRagServerExtension.getBaseUrl().toString());
        configuration.addProperty("scribe.token", "dummy-token");
        configuration.addProperty("scribe.ssl.trust.all.certs", "true");
        ScribeConfiguration ragConfig = ScribeConfiguration.from(configuration);
        client = new ScribeClient(ragConfig);
    }

    @Test
    void proxyChatCompletionsShouldForwardPayloadAndHeadersAndReturnExactResponse() {
        String resultBody = """
            {
              "id": "1234",
              "choices": [
                {
                  "finish_reason": "stop",
                  "index": 0,
                  "message": {
                    "content": "The glass green worm around Anvers"
                  }
                }
              ]
            }
            """;

        wireMockRagServerExtension.setChatCompletionResponse(200, resultBody);

        String incomingPayload = """
            {
               "messages" : [
                 {"role": "system", "content": "You are a translator."},
                 {"role": "user", "content": "Translate this text:"},
                 {"role": "user", "content": "Le ver vert en verre vers Anvers"}
               ],
                "metadata": {
                 "action": "translate"
               },
               "stream": false
            }""";

        ChatCompletionResult result = client.proxyChatCompletions(incomingPayload.getBytes(StandardCharsets.UTF_8)).block();

        verify(1, postRequestedFor(urlMatching(CHAT_COMPLETIONS_ENDPOINT)));

        verify(postRequestedFor(urlMatching(CHAT_COMPLETIONS_ENDPOINT))
            .withHeader("Authorization", equalTo("Bearer dummy-token"))
            .withHeader("Content-Type", containing("application/json"))
            .withRequestBody(equalToJson(incomingPayload)));

        String body = new String(result.body(), StandardCharsets.UTF_8);
        SoftAssertions.assertSoftly(solftly -> {
            solftly.assertThat(result.status()).isEqualTo(200);
            solftly.assertThat(result.headers().get("Content-Type")).isEqualTo("application/json");
            solftly.assertThat(body).isEqualTo(resultBody);
        });
    }

    @Test
    void proxyChatCompletionsShouldReturnExactErrorResponses() {
        String resultBody = """
            {
              "details": "Internal server error..."
            }
            """;

        String incomingPayload = "{bad_payload}";

        wireMockRagServerExtension.setChatCompletionResponse(500, resultBody);

        ChatCompletionResult result = client.proxyChatCompletions(incomingPayload.getBytes(StandardCharsets.UTF_8)).block();
        String body = new String(result.body(), StandardCharsets.UTF_8);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result.status()).isEqualTo(500);
            softly.assertThat(result.headers().get("Content-Type")).isEqualTo("application/json");
            softly.assertThat(body).isEqualTo(resultBody);
        });
    }

}
