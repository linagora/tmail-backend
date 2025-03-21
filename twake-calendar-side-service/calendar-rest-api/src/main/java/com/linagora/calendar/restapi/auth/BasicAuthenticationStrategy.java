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

import java.util.Base64;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.jmap.http.AuthenticationChallenge;
import org.apache.james.jmap.http.AuthenticationScheme;
import org.apache.james.jmap.http.AuthenticationStrategy;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.ReactorUtils;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.server.HttpServerRequest;

// Duplicated not to depend on JMAP RFC-8620 module of James
public class BasicAuthenticationStrategy implements AuthenticationStrategy {
    public record UserCredentials(Username username, String password) {

    }

    private static CharMatcher CHAR_MATCHER = CharMatcher.inRange('a', 'z')
        .or(CharMatcher.inRange('0', '9'))
        .or(CharMatcher.inRange('A', 'Z'))
        .or(CharMatcher.is('_'))
        .or(CharMatcher.is('='))
        .or(CharMatcher.is('-'))
        .or(CharMatcher.is('#'));

    private static Optional<UserCredentials> parseUserCredentials(String authHeader) {
        if (authHeader.startsWith("Basic ")) {
            String base64 = authHeader.substring(6);
            Preconditions.checkArgument(CHAR_MATCHER.matchesAllOf(base64), "Invalid char in basic auth string");
            String decoded = new String(Base64.getDecoder().decode(base64));
            Preconditions.checkArgument(decoded.contains(":"), "Invalid decoded basic auth token: it must contain ':'");
            int partSeparatorIndex = decoded.indexOf(':');
            String usernameString = decoded.substring(0, partSeparatorIndex);
            String passwordString = decoded.substring(partSeparatorIndex + 1);

            return Optional.of(new UserCredentials(Username.of(usernameString), passwordString));
        }
        return Optional.empty();
    }

    private final UsersRepository usersRepository;
    private final SimpleSessionProvider sessionProvider;
    private final MetricFactory metricFactory;

    @Inject
    public BasicAuthenticationStrategy(UsersRepository usersRepository, SimpleSessionProvider sessionProvider, MetricFactory metricFactory) {
        this.usersRepository = usersRepository;
        this.sessionProvider = sessionProvider;
        this.metricFactory = metricFactory;
    }

    @Override
    public Mono<MailboxSession> createMailboxSession(HttpServerRequest httpRequest) {
        return Mono.fromCallable(() -> authHeaders(httpRequest))
            .map(BasicAuthenticationStrategy::parseUserCredentials)
            .handle(ReactorUtils.publishIfPresent())
            .flatMap(this::authenticate)
            .map(sessionProvider::createSession);
    }

    private Mono<Username> authenticate(UserCredentials creds) {
        return Mono.from(metricFactory.decoratePublisherWithTimerMetric("basic-auth",
            Mono.fromCallable(() -> usersRepository.test(creds.username(), creds.password())
                .orElseThrow(() -> new UnauthorizedException("Wrong credentials provided")))
                .subscribeOn(Schedulers.boundedElastic())));
    }

    @Override
    public AuthenticationChallenge correspondingChallenge() {
        return AuthenticationChallenge.of(AuthenticationScheme.of("Basic"), ImmutableMap.of("realm", "simple"));
    }
}
