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

import java.net.URL;
import java.time.Instant;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.core.Username;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.jwt.DefaultCheckTokenClient;
import org.apache.james.jwt.introspection.IntrospectionEndpoint;
import org.apache.james.jwt.introspection.TokenIntrospectionResponse;
import org.apache.james.jwt.userinfo.UserinfoResponse;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.streams.Iterators;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class OidcEndpointsInfoResolver implements TokenInfoResolver {
    private static final String SID_PROPERTY = "sid";

    private final DefaultCheckTokenClient checkTokenClient;
    private final MetricFactory metricFactory;
    private final URL userInfoURL;
    private final IntrospectionEndpoint introspectionEndpoint;
    private final JMAPOidcConfiguration configuration;

    @Inject
    public OidcEndpointsInfoResolver(DefaultCheckTokenClient checkTokenClient,
                                     MetricFactory metricFactory,
                                     @Named("userInfo") URL userInfoURL,
                                     IntrospectionEndpoint introspectionEndpoint,
                                     JMAPOidcConfiguration configuration) {
        this.checkTokenClient = checkTokenClient;
        this.metricFactory = metricFactory;
        this.userInfoURL = userInfoURL;
        this.introspectionEndpoint = introspectionEndpoint;
        this.configuration = configuration;
    }

    @Override
    public Mono<TokenInfo> apply(Token token) {
        return Mono.zip(
                Mono.from(metricFactory.decoratePublisherWithTimerMetric("userinfo-lookup", checkTokenClient.userInfo(userInfoURL, token.value()))),
                Mono.from(metricFactory.decoratePublisherWithTimerMetric("introspection-lookup", checkTokenClient.introspect(introspectionEndpoint, token.value()))))
            .flatMap(tokenInfos -> {
                UserinfoResponse userInfo = tokenInfos.getT1();
                TokenIntrospectionResponse introspectInfo = tokenInfos.getT2();

                Username sub = Username.of(userInfo.claimByPropertyName(configuration.getOidcClaim())
                    .orElseThrow(() -> new UnauthorizedException("Invalid OIDC token: userinfo needs to include " + configuration.getOidcClaim() + " claim")));

                return Mono.just(toTokenInfo(sub, userInfo, introspectInfo));
            });
    }

    private TokenInfo toTokenInfo(Username username, UserinfoResponse userinfoResponse, TokenIntrospectionResponse introspectionResponse) {
        return new TokenInfo(
            username.asString(),
            userinfoResponse.claimByPropertyName(SID_PROPERTY).map(Sid::new)
                .or(() -> introspectionResponse.claimByPropertyName(SID_PROPERTY).map(Sid::new)),
            Instant.ofEpochSecond(introspectionResponse.exp().orElseThrow(() -> new UnauthorizedException("Expiration claim ('exp') is required in the token"))),
            extractAudience(introspectionResponse));
    }

    private static List<Aud> extractAudience(TokenIntrospectionResponse introspectionResponse) {
        JsonNode audJson = introspectionResponse.json().get("aud");
        if (audJson.isArray()) {
            return Iterators.toStream(audJson.iterator())
                .map(JsonNode::asText)
                .map(Aud::new)
                .toList();
        }
        return ImmutableList.of(new Aud(audJson.asText()));
    }
}
