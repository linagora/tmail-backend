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

import jakarta.inject.Inject;

import com.linagora.tmail.mailet.conf.AiHttpClientConfiguration;
import com.linagora.tmail.mailet.rag.httpclient.AiHttpClientFactory;

import io.netty.buffer.Unpooled;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public class ScribeClient {

    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String CHAT_COMPLETIONS_ENDPOINT = "/v1/chat/completions";

    private final HttpClient httpClient;

    @Inject
    public ScribeClient(ScribeConfiguration configuration) {
        this.httpClient = AiHttpClientFactory.create(AiHttpClientConfiguration.from(configuration));
    }

    public Mono<ChatCompletionResult> proxyChatCompletions(byte[] payload) {
        return httpClient
            .headers(headers -> headers.add(CONTENT_TYPE_HEADER, APPLICATION_JSON))
            .post()
            .uri(CHAT_COMPLETIONS_ENDPOINT)
            .send(Mono.just(Unpooled.wrappedBuffer(payload)))
            .responseSingle((response, content) -> content
                .asByteArray()
                .map(body -> new ChatCompletionResult(body, response.status().code(), response.responseHeaders())));
    }
}
