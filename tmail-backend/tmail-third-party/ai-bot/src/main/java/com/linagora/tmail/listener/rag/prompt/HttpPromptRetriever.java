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

import java.net.URL;

import javax.net.ssl.SSLException;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;



public class HttpPromptRetriever implements PromptRetriever {

    private final HttpClient httpClient;

    public HttpPromptRetriever(String url) {
        this.httpClient = HttpClient.create();
    }

    @Override
    public Mono<String> retrievePrompt(URL url) {
        if (url == null) {
            return Mono.error(new PromptRetrievalException("Prompt URL must not be null"));
        }
        return httpClient
            .get()
            .uri(url.toExternalForm())
            .responseSingle((response, content) -> {
                int code = response.status().code();
                if (code != 200) {
                    return Mono.error(new PromptRetrievalException("Prompt download failed (" + code + ") for " + url.toExternalForm()));
                }
                return content
                    .asString();
            });
    }


    private static SslContext buildInsecureSslContext() {
        try {
            return SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        } catch (SSLException e) {
            throw new RuntimeException("Failed to create insecure SSL context", e);
        }
    }
}
