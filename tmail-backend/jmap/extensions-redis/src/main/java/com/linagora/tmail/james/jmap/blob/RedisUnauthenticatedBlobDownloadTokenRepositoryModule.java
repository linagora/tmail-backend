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

package com.linagora.tmail.james.jmap.blob;

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

public class RedisUnauthenticatedBlobDownloadTokenRepositoryModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisUnauthenticatedBlobDownloadTokenRepositoryModule.class);

    @Override
    protected void configure() {
        bind(UnauthenticatedBlobDownloadTokenRepository.class).to(RedisUnauthenticatedBlobDownloadTokenRepository.class)
            .in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    public RedisUnauthenticatedBlobDownloadTokenRepositoryCommands provideRedisUnauthenticatedBlobDownloadTokenRepositoryCommands(
        RedisReactiveCommandsFactory redisReactiveCommandsFactory,
        RedisUnauthenticatedBlobDownloadTokenRepositoryConfiguration configuration) {
        Duration commandTimeout = configuration.commandTimeout();

        return redisReactiveCommandsFactory.create(
            commands -> RedisUnauthenticatedBlobDownloadTokenRepositoryCommands.of(commands, commandTimeout),
            commands -> RedisUnauthenticatedBlobDownloadTokenRepositoryCommands.of(commands, commandTimeout));
    }

    @Provides
    @Singleton
    public RedisConfiguration redisConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        try {
            return RedisConfiguration.from(propertiesProvider.getConfiguration("redis"));
        } catch (FileNotFoundException e) {
            LOGGER.error("Missing `redis.properties` configuration file for unauthenticated blob access token storage.");
            throw e;
        }
    }

    @Provides
    @Singleton
    public RedisUnauthenticatedBlobDownloadTokenRepositoryConfiguration redisUnauthenticatedBlobDownloadTokenRepositoryConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return RedisUnauthenticatedBlobDownloadTokenRepositoryConfiguration.from(propertiesProvider.getConfiguration("redis"));
        } catch (FileNotFoundException e) {
            LOGGER.info("Missing `redis.properties` configuration file -> using default RedisUnauthenticatedBlobDownloadTokenRepositoryConfiguration");
            return RedisUnauthenticatedBlobDownloadTokenRepositoryConfiguration.DEFAULT;
        }
    }
}
