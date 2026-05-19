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
import java.util.List;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.redis.ClusterRedisConfiguration;
import org.apache.james.backends.redis.MasterReplicaRedisConfiguration;
import org.apache.james.backends.redis.RedisClientFactory;
import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.backends.redis.SentinelRedisConfiguration;
import org.apache.james.backends.redis.StandaloneRedisConfiguration;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.linagora.tmail.james.jmap.redis.RedisConfigurationUtils;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.masterreplica.MasterReplica;
import io.lettuce.core.masterreplica.StatefulRedisMasterReplicaConnection;

public class RedisUnauthenticatedBlobDownloadTokenRepositoryModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisUnauthenticatedBlobDownloadTokenRepositoryModule.class);

    @Override
    protected void configure() {
        bind(UnauthenticatedBlobDownloadTokenRepository.class).to(RedisUnauthenticatedBlobDownloadTokenRepository.class)
            .in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    public RedisUnauthenticatedBlobDownloadTokenRepositoryCommands provideRedisUnauthenticatedBlobDownloadTokenRepositoryCommands(RedisClientFactory redisClientFactory,
                                                                                                                                  RedisConfiguration redisConfiguration) {
        AbstractRedisClient rawClient = redisClientFactory.rawRedisClient();

        return switch (redisConfiguration) {
            case StandaloneRedisConfiguration ignored ->
                RedisUnauthenticatedBlobDownloadTokenRepositoryCommands.of(((RedisClient) rawClient).connect(StringCodec.UTF8).reactive());

            case ClusterRedisConfiguration clusterConfiguration -> {
                RedisClusterClient client = (RedisClusterClient) rawClient;
                StatefulRedisClusterConnection<String, String> connection = client.connect(StringCodec.UTF8);
                connection.setReadFrom(clusterConfiguration.readFrom());
                yield RedisUnauthenticatedBlobDownloadTokenRepositoryCommands.of(connection.reactive());
            }

            case SentinelRedisConfiguration sentinelConfiguration -> {
                StatefulRedisMasterReplicaConnection<String, String> sentinelConnection = MasterReplica.connect((RedisClient) rawClient, StringCodec.UTF8, sentinelConfiguration.redisURI());
                sentinelConnection.setReadFrom(sentinelConfiguration.readFrom());
                yield RedisUnauthenticatedBlobDownloadTokenRepositoryCommands.of(sentinelConnection.reactive());
            }

            case MasterReplicaRedisConfiguration replicaConfiguration -> {
                List<RedisURI> uris = RedisConfigurationUtils.asJavaRedisUris(replicaConfiguration.redisURI());
                StatefulRedisMasterReplicaConnection<String, String> masterReplicaConnection = MasterReplica.connect((RedisClient) rawClient, StringCodec.UTF8, uris);
                masterReplicaConnection.setReadFrom(replicaConfiguration.readFrom());
                yield RedisUnauthenticatedBlobDownloadTokenRepositoryCommands.of(masterReplicaConnection.reactive());
            }

            default ->
                throw new RuntimeException("Unknown redis configuration type: " + redisConfiguration.getClass().getName());
        };
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
}
