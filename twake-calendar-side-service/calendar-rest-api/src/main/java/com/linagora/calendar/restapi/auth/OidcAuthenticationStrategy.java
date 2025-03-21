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

import java.net.URL;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.core.Username;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.jmap.http.AuthenticationChallenge;
import org.apache.james.jmap.http.AuthenticationScheme;
import org.apache.james.jmap.http.AuthenticationStrategy;
import org.apache.james.jwt.DefaultCheckTokenClient;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableMap;
import com.linagora.calendar.restapi.RestApiConfiguration;

import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

public class OidcAuthenticationStrategy implements AuthenticationStrategy {

    private final DefaultCheckTokenClient checkTokenClient;
    private final SimpleSessionProvider sessionProvider;
    private final MetricFactory metricFactory;
    private final URL userInfoURL;
    private final RestApiConfiguration configuration;

    @Inject
    public OidcAuthenticationStrategy(SimpleSessionProvider sessionProvider, RestApiConfiguration configuration,
                                      @Named("userInfo") URL userInfoURL, MetricFactory metricFactory) {
        this.sessionProvider = sessionProvider;
        this.metricFactory = metricFactory;
        this.checkTokenClient = new DefaultCheckTokenClient();
        this.configuration = configuration;
        this.userInfoURL = userInfoURL;
    }

    @Override
    public Mono<MailboxSession> createMailboxSession(HttpServerRequest httpRequest) {
        return Mono.fromCallable(() -> authHeaders(httpRequest))
            .filter(header -> header.startsWith(AUTHORIZATION_HEADER_PREFIX))
            .map(header -> header.substring(AUTHORIZATION_HEADER_PREFIX.length()))
            .filter(token -> !token.startsWith("eyJ")) // Heuristic for detecting JWT
            .flatMap(token -> Mono.from(metricFactory.decoratePublisherWithTimerMetric("userinfo-lookup",
                    checkTokenClient.userInfo(userInfoURL, token))))
            .map(x -> x.claimByPropertyName(configuration.getOidcClaim())
                .orElseThrow(() -> new UnauthorizedException("Invalid OIDC token: introspection needs to include email claim")))
            .map(Username::of)
            .map(Throwing.function(sessionProvider::createSession));
    }

    @Override
    public AuthenticationChallenge correspondingChallenge() {
        return AuthenticationChallenge.of(
            AuthenticationScheme.of("Bearer"),
            ImmutableMap.of("realm", "twake_calendar",
                "error", "invalid_token",
                "scope", "openid profile email"));
    }
}
