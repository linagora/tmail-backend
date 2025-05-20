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

package com.linagora.tmail.james.jmap.oidc;

import static org.apache.james.jmap.http.JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX;

import java.time.Clock;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.jmap.http.AuthenticationChallenge;
import org.apache.james.jmap.http.AuthenticationScheme;
import org.apache.james.jmap.http.AuthenticationStrategy;
import org.apache.james.jwt.introspection.TokenIntrospectionException;
import org.apache.james.jwt.userinfo.UserInfoCheckException;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SessionProvider;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

public class OidcAuthenticationStrategy implements AuthenticationStrategy {
    private final SessionProvider sessionProvider;
    private final OidcTokenCache oidcTokenCache;
    private final Clock clock;
    private final List<Aud> auds;

    @Inject
    public OidcAuthenticationStrategy(SessionProvider sessionProvider, OidcTokenCache oidcTokenCache, Clock clock, List<Aud> auds) {
        this.sessionProvider = sessionProvider;
        this.oidcTokenCache = oidcTokenCache;
        this.clock = clock;
        this.auds = auds;
    }


    @Override
    public Mono<MailboxSession> createMailboxSession(HttpServerRequest httpRequest) {
        return Mono.fromCallable(() -> authHeaders(httpRequest))
            .filter(header -> header.startsWith(AUTHORIZATION_HEADER_PREFIX))
            .map(header -> header.substring(AUTHORIZATION_HEADER_PREFIX.length()))
            .map(Token::new)
            .flatMap(oidcTokenCache::associatedInformation)
            .<TokenInfo>handle((tokenInfo, sink) -> {
                if (!auds.isEmpty() && !isAudienceAccepted(tokenInfo.aud())) {
                    sink.error(new UnauthorizedException("Wrong audience. Expected " + auds + " got " + tokenInfo.aud()));
                    return;
                }
                if (clock.instant().isAfter(tokenInfo.exp())) {
                    sink.error(new UnauthorizedException("Expired token"));
                    return;
                }

                sink.next(tokenInfo);
            })
            .map(tokenInfo -> Username.of(tokenInfo.email()))
            .map(Throwing.function(sessionProvider::createSystemSession))
            .onErrorMap(TokenIntrospectionException.class, e -> new UnauthorizedException("Invalid OIDC token when introspection check", e))
            .onErrorMap(UserInfoCheckException.class, e -> new UnauthorizedException("Invalid OIDC token when user info check", e));
    }

    private boolean isAudienceAccepted(List<Aud> tokenAudiences) {
        for (Aud aud: auds) {
            if (tokenAudiences.contains(aud)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public AuthenticationChallenge correspondingChallenge() {
        return AuthenticationChallenge.of(
            AuthenticationScheme.of("Bearer"),
            ImmutableMap.of("realm", "twake_mail",
                "error", "invalid_token",
                "scope", "openid profile email"));
    }
}
