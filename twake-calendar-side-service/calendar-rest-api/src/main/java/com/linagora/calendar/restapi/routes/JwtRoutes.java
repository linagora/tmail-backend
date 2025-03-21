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

package com.linagora.calendar.restapi.routes;

import jakarta.inject.Inject;

import org.apache.james.jmap.Endpoint;
import org.apache.james.jmap.http.Authenticator;
import org.apache.james.mailbox.MailboxSession;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class JwtRoutes extends CalendarRoute {
    private final JwtSigner jwtSigner;

    @Inject
    public JwtRoutes(Authenticator authenticator, JwtSigner jwtSigner) {
        super(authenticator);
        this.jwtSigner = jwtSigner;
    }

    @Override
    Endpoint endpoint() {
        return Endpoint.ofFixedPath(HttpMethod.POST, "/api/jwt/generate");
    }

    @Override
    Mono<Void> handleRequest(HttpServerRequest request, HttpServerResponse response, MailboxSession session) {
        return response.status(HttpResponseStatus.OK)
            .header(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8")
            .sendString(Mono.fromCallable(() -> jwtSigner.generate(session.getUser().asString())).subscribeOn(Schedulers.parallel())
                .map(JwtRoutes::quote))
            .then();
    }

    private static String quote(String s) {
        return "\"" + s + "\"";
    }
}
