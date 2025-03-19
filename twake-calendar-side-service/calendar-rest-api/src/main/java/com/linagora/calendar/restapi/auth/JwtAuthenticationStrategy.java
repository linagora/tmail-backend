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

package com.linagora.calendar.restapi.auth;

import static org.apache.james.jmap.http.JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.Username;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.jmap.http.AuthenticationChallenge;
import org.apache.james.jmap.http.AuthenticationScheme;
import org.apache.james.jmap.http.AuthenticationStrategy;
import org.apache.james.jwt.JwtConfiguration;
import org.apache.james.jwt.JwtTokenVerifier;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.util.ReactorUtils;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableMap;
import com.linagora.calendar.restapi.RestApiConfiguration;

import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

public class JwtAuthenticationStrategy implements AuthenticationStrategy {
    private final JwtTokenVerifier jwtTokenVerifier;
    private final UsersRepository usersRepository;
    private final SimpleSessionProvider sessionProvider;

    @Inject
    public JwtAuthenticationStrategy(UsersRepository usersRepository, SimpleSessionProvider sessionProvider, FileSystem fileSystem, RestApiConfiguration configuration) {
        this.usersRepository = usersRepository;
        this.sessionProvider = sessionProvider;
        List<String> loadedKeys = configuration.getJwtPublicPath()
            .stream()
            .map(Throwing.function(path -> IOUtils.toString(fileSystem.getResource(path), StandardCharsets.UTF_8)))
            .collect(Collectors.toList());
        this.jwtTokenVerifier = JwtTokenVerifier.create(new JwtConfiguration(loadedKeys));
    }

    @Override
    public Mono<MailboxSession> createMailboxSession(HttpServerRequest httpRequest) {
        return Mono.fromCallable(() -> authHeaders(httpRequest))
            .filter(header -> header.startsWith(AUTHORIZATION_HEADER_PREFIX))
            .map(header -> header.substring(AUTHORIZATION_HEADER_PREFIX.length()))
            .filter(token -> token.startsWith("eyJ")) // Heuristic for detecting JWT
            .flatMap(userJWTToken -> Mono.fromCallable(() -> {
                Username username = jwtTokenVerifier.verifyAndExtractLogin(userJWTToken)
                    .map(Username::of)
                    .orElseThrow(() -> new UnauthorizedException("Failed Jwt verification"));
                try {
                    usersRepository.assertValid(username);
                } catch (UsersRepositoryException e) {
                    throw new UnauthorizedException("Invalid username", e);
                }

                return username;
            }).subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER))
            .map(Throwing.function(sessionProvider::createSession));
    }

    @Override
    public AuthenticationChallenge correspondingChallenge() {
        return AuthenticationChallenge.of(
            AuthenticationScheme.of("Bearer"),
            ImmutableMap.of("realm", "JWT"));
    }
}
