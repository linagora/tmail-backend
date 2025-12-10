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

package com.linagora.tmail.extension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;

import org.apache.james.GuiceModuleTestExtension;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.linagora.tmail.mailet.rag.RagConfig;

public class WireMockRagServerExtension extends WireMockExtension implements GuiceModuleTestExtension {
    private static final String CHAT_COMPLETIONS_ENDPOINT = "/v1/chat/completions";
    private static final String RAG_INDEXER_ENDPOINT = "/indexer/partition/.*/file/.*";

    public WireMockRagServerExtension(Builder builder) {
        super(builder);
    }

    public WireMockRagServerExtension() {
        this(WireMockExtension.extensionOptions()
            .options(wireMockConfig().dynamicPort()));
    }

    public URL getBaseUrl() {
        try {
            return URI.create(baseUrl()).toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Module getModule() {
        return new AbstractModule() {
            @Provides
            @Singleton
            public RagConfig ragConfig() {
                return new RagConfig("fake-token", true, Optional.of(getBaseUrl()), "{localPart}.twake.{domainName}");
            }
        };
    }

    @Override
    protected void onBeforeEach(WireMockRuntimeInfo wireMockRuntimeInfo) {
        super.onBeforeEach(wireMockRuntimeInfo);

        configureFor("localhost", this.getPort());
    }

    public void setChatCompletionResponse(int status, String resultBody) {
        stubFor(post(urlPathMatching(CHAT_COMPLETIONS_ENDPOINT))
            .willReturn(aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(resultBody)));
    }

    public void setRagIndexerPostResponse(int status, String resultBody) {
        stubFor(post(urlPathMatching(RAG_INDEXER_ENDPOINT))
            .willReturn(aResponse()
                .withStatus(status)
                .withBody(resultBody)));
    }

    public void setRagIndexerPutResponse(int status, String resultBody) {
        stubFor(put(urlPathMatching(RAG_INDEXER_ENDPOINT))
            .willReturn(aResponse()
                .withStatus(status)
                .withBody(resultBody)));
    }
}
