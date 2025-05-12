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
 *******************************************************************/

package com.linagora.tmail.james.jmap.oidc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;

import io.lettuce.core.KeyValue;
import reactor.core.publisher.Mono;

public class RedisOidcTokenCache implements OidcTokenCache {
    public static class TokenParseException extends RuntimeException {
        public TokenParseException(String message, Throwable cause) {
            super(message, cause);
        }

        public TokenParseException(String message) {
            super(message);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisOidcTokenCache.class);

    private static final String TOKEN_CACHE_PREFIX = "tmail_oidc_hash_";
    private static final String SID_CACHE_PREFIX = "tmail_oidc_sid_";
    private static final String AUD_DELIMITER = ";";

    public interface TokenFields {
        String EMAIL = "email";
        String EXP = "exp";
        String AUD = "aud";
        String SID = "sid";
    }

    private final RedisTokenCacheCommands redisCommands;
    private final TokenInfoResolver tokenInfoResolver;
    private final OidcTokenCacheConfiguration tokenCacheConfiguration;

    public RedisOidcTokenCache(TokenInfoResolver tokenInfoResolver,
                               OidcTokenCacheConfiguration oidcTokenCacheConfiguration,
                               RedisTokenCacheCommands redisCommands) {
        this.tokenInfoResolver = tokenInfoResolver;
        this.tokenCacheConfiguration = oidcTokenCacheConfiguration;
        this.redisCommands = redisCommands;
    }

    @Override
    public Mono<Void> invalidate(Sid sid) {
        String sidRedisKey = resolveSidRedisKey(sid);
        return redisCommands.lrange(sidRedisKey)
            .collectList()
            .filter(tokenRedisKeyList -> !tokenRedisKeyList.isEmpty())
            .flatMap(tokenRedisKeyList -> redisCommands.del(tokenRedisKeyList.toArray(String[]::new)))
            .then(redisCommands.del(sidRedisKey))
            .then()
            .onErrorResume(error -> {
                LOGGER.warn("Failed to invalidate token cache for sid={}", sid.value(), error);
                return Mono.empty();
            });
    }

    @Override
    public Mono<TokenInfo> associatedInformation(Token token) {
        return Mono.fromCallable(() -> resolveTokenRedisKey(token))
            .flatMap(tokenRedisKey -> getTokenInfoFromCache(tokenRedisKey)
                .onErrorResume(error -> {
                    LOGGER.warn("Failed to get username from cache for token={}", token.value(), error);
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() -> resolveTokenInfoAndCache(token, tokenRedisKey))));
    }

    public Mono<TokenInfo> getTokenInfoFromCache(String tokenRedisKey) {
        return redisCommands.hgetall(tokenRedisKey)
            .collectMap(KeyValue::getKey, KeyValue::getValue)
            .filter(mapData -> !mapData.isEmpty())
            .map(this::parseTokenInfo);
    }

    private TokenInfo parseTokenInfo(Map<String, String> mapData) throws TokenParseException {
        String rawEmail = mapData.get(TokenFields.EMAIL);
        String rawSid = mapData.get(TokenFields.SID);
        String rawExp = mapData.get(TokenFields.EXP);
        String rawAud = mapData.get(TokenFields.AUD);

        if (StringUtils.isBlank(rawEmail) || StringUtils.isBlank(rawExp) || StringUtils.isBlank(rawAud)) {
            throw new TokenParseException("Missing required fields in token data: " + mapData);
        }
        try {
            Optional<Sid> sid = Optional.ofNullable(rawSid)
                .filter(StringUtils::isNotBlank)
                .map(Sid::new);
            Instant exp = Instant.ofEpochSecond(Long.parseLong(rawExp));

            List<Aud> audList = Splitter.on(AUD_DELIMITER)
                .omitEmptyStrings()
                .splitToStream(rawAud)
                .map(Aud::new)
                .toList();
            return new TokenInfo(rawEmail, sid, exp, audList);
        } catch (Exception e) {
            throw new TokenParseException("Failed to parse token fields from cache: " + mapData, e);
        }
    }

    private Mono<TokenInfo> resolveTokenInfoAndCache(Token token, String tokenRedisKey) {
        return tokenInfoResolver.apply(token)
            .flatMap(tokenInfo -> cacheTokenInfo(tokenRedisKey, tokenInfo)
                .thenReturn(tokenInfo));
    }

    private Mono<Void> cacheTokenInfo(String tokenRedisKey, TokenInfo tokenInfo) {
        return cacheAssociatedInformation(tokenRedisKey, tokenInfo)
            .then(Mono.justOrEmpty(tokenInfo.sid())
                .flatMap(sidValue -> cacheSidInfo(sidValue, tokenRedisKey)))
            .then()
            .onErrorResume(error -> {
                LOGGER.warn("Failed to cache token info: {}", tokenInfo.asString(), error);
                return Mono.empty();
            });
    }

    private Mono<Void> cacheAssociatedInformation(String tokenRedisKey, TokenInfo tokenInfo) {
        Map<String, String> mapData = ImmutableMap.of(
            TokenFields.EMAIL, tokenInfo.email(),
            TokenFields.EXP, String.valueOf(tokenInfo.exp().getEpochSecond()),
            TokenFields.AUD, Joiner.on(AUD_DELIMITER).skipNulls().join(Lists.transform(tokenInfo.aud(), Aud::value)),
            TokenFields.SID, tokenInfo.sid().map(Sid::value).orElse(StringUtils.EMPTY));

        return redisCommands.hset(tokenRedisKey, mapData)
            .then(redisCommands.expire(tokenRedisKey, tokenCacheConfiguration.expiration()));
    }

    private Mono<String> cacheSidInfo(Sid sid, String tokenRedisKey) {
        return Mono.fromCallable(() -> resolveSidRedisKey(sid))
            .flatMap(sidRedisKey -> redisCommands.rpush(sidRedisKey, tokenRedisKey)
                .then(redisCommands.expire(sidRedisKey, tokenCacheConfiguration.expiration()))
                .thenReturn(sidRedisKey));
    }

    private String resolveTokenRedisKey(Token token) {
        return TOKEN_CACHE_PREFIX + Hashing.sha512().hashString(token.value(), StandardCharsets.UTF_8);
    }

    private String resolveSidRedisKey(Sid sid) {
        return SID_CACHE_PREFIX + sid.value();
    }
}
