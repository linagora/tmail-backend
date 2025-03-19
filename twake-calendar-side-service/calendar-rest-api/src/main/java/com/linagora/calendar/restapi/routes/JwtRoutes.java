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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.time.Clock;

import jakarta.inject.Inject;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.jmap.Endpoint;
import org.apache.james.jmap.http.Authenticator;
import org.apache.james.mailbox.MailboxSession;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.io.pem.PemReader;

import com.linagora.calendar.restapi.RestApiConfiguration;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class JwtRoutes extends CalendarRoute {

    private final JwtSigner jwtSigner;

    public static PrivateKey loadPrivateKey(Path pemFilePath) throws Exception {
        // Read PEM file content
        try (PEMParser pemParser = new PEMParser(new PemReader(Files.newBufferedReader(pemFilePath)))) {
            Object o = pemParser.readObject();
            if (o instanceof PEMKeyPair keyPair) {
                return new JcaPEMKeyConverter().getPrivateKey(keyPair.getPrivateKeyInfo());
            }
            throw new RuntimeException("Invalid key of class " + o.getClass());
        }
    }

    @Inject
    public JwtRoutes(Authenticator authenticator, RestApiConfiguration configuration, Clock clock, FileSystem fileSystem) throws Exception {
        super(authenticator);
        File file = fileSystem.getFile(configuration.getJwtPrivatePath());
        jwtSigner = new JwtSigner(clock, configuration.getJwtValidity(), loadPrivateKey(file.toPath()));
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
