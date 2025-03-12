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

package com.linagora.calendar.restapi;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class FallbackProxy {
    private final HttpClient client;
    private final RestApiConfiguration configuration;

    public FallbackProxy(RestApiConfiguration configuration) {
        this.configuration = configuration;
        client = HttpClient.create();
    }

    public Mono<Void> forwardRequest(HttpServerRequest request, HttpServerResponse response) {
        return client.headers(headers -> headers.add(request.requestHeaders()))
            .request(request.method())
            .uri(configuration.getOpenpaasBackendURL().toString() + request.uri())
            .send((req, out) -> out.send(request.receive().retain()))
            .response((res, in) -> {
                response.status(res.status());
                res.responseHeaders().forEach(entry -> response.addHeader(entry.getKey(), entry.getValue()));
                return response.send(in.retain());
            })
            .then();
    }
}
