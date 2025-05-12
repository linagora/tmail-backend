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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.james.backends.redis.RedisClientFactory;
import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.core.Username;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.google.common.hash.Hashing;

public abstract class RedisOidcTokenCacheContract extends OidcTokenCacheContract {

    public abstract void pauseRedis();

    private RedisOidcTokenCache redisOIDCTokenCache;

    @BeforeEach
    void setUp() {
        RedisConfiguration redisConfiguration = redisConfiguration();
        RedisClientFactory redisClientFactory = new RedisClientFactory(FileSystemImpl.forTesting(), redisConfiguration);
        redisOIDCTokenCache = new RedisOidcTokenCacheModule().provideRedisOIDCTokenCache(redisClientFactory, redisConfiguration,
            OidcTokenCacheConfiguration.DEFAULT, tokenInfoResolver);
    }

    @Override
    public OidcTokenCache testee() {
        return redisOIDCTokenCache;
    }

    public abstract RedisConfiguration redisConfiguration();

    @Override
    public Optional<Username> getUsernameFromCache(Token token) {
        return redisOIDCTokenCache.getTokenInfoFromCache("tmail_oidc_hash_" + Hashing.sha512().hashString(token.value(), StandardCharsets.UTF_8))
            .map(TokenInfo::email)
            .map(Username::of)
            .blockOptional();
    }

    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @Test
    public void associatedUsernameShouldStillReturnValueWhenRedisIsDown() {
        mockTokenInfoResolverSuccess(token, TOKEN_INFO);

        pauseRedis();

        assertThat(testee().associatedInformation(token).block())
            .isEqualTo(TOKEN_INFO);
    }

    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @Test
    public void invalidateShouldSwallowRedisError() {
        pauseRedis();
        assertThatCode(() -> testee().invalidate(SID).block())
            .doesNotThrowAnyException();
    }

    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @Test
    public void shouldFallbackGracefullyWhenRedisIsDown() {
        mockTokenInfoResolverSuccess(token, TOKEN_INFO);
        testee().associatedInformation(token).block();

        pauseRedis();

        assertThatCode(() -> testee().invalidate(SID).block())
            .doesNotThrowAnyException();

        assertThat(testee().associatedInformation(token).block())
            .isEqualTo(TOKEN_INFO);
    }
}
