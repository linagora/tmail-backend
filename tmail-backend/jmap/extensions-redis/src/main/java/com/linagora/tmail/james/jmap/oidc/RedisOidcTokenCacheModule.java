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

import java.io.FileNotFoundException;
import java.time.Duration;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.linagora.tmail.james.jmap.redis.RedisReactiveCommandsFactory;

public class RedisOidcTokenCacheModule extends AbstractModule {
    public static final Logger LOGGER = LoggerFactory.getLogger(RedisOidcTokenCacheModule.class);

    @Override
    protected void configure() {
        bind(OidcTokenCache.class).to(RedisOidcTokenCache.class)
            .in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    public RedisOidcTokenCache provideRedisOIDCTokenCache(RedisReactiveCommandsFactory redisReactiveCommandsFactory,
                                                          OidcTokenCacheConfiguration oidcTokenCacheConfiguration,
                                                          RedisOidcTokenCacheConfiguration redisOidcTokenCacheConfiguration,
                                                          TokenInfoResolver tokenInfoResolver) {
        Duration commandTimeout = redisOidcTokenCacheConfiguration.commandTimeout();
        RedisTokenCacheCommands redisReactiveCommands = redisReactiveCommandsFactory.create(
            commands -> RedisTokenCacheCommands.of(commands, commandTimeout),
            commands -> RedisTokenCacheCommands.of(commands, commandTimeout));

        return new RedisOidcTokenCache(tokenInfoResolver, oidcTokenCacheConfiguration, redisReactiveCommands);
    }

    @Provides
    @Singleton
    public RedisConfiguration redisConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        try {
            return RedisConfiguration.from(propertiesProvider.getConfiguration("redis"));
        } catch (FileNotFoundException e) {
            LOGGER.error("Missing `redis.properties` configuration file for Redis OIDC token cache usage.");
            throw e;
        }
    }

    @Provides
    @Singleton
    public RedisOidcTokenCacheConfiguration redisOidcTokenCacheConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return RedisOidcTokenCacheConfiguration.from(propertiesProvider.getConfiguration("redis"));
        } catch (FileNotFoundException e) {
            LOGGER.info("Missing `redis.properties` configuration file -> using default RedisOidcTokenCacheConfiguration");
            return RedisOidcTokenCacheConfiguration.DEFAULT;
        }
    }
}
