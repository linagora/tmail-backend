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

package com.linagora.tmail.james.jmap.redis;

import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.backends.redis.ClusterRedisConfiguration;
import org.apache.james.backends.redis.MasterReplicaRedisConfiguration;
import org.apache.james.backends.redis.RedisClientFactory;
import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.backends.redis.SentinelRedisConfiguration;
import org.apache.james.backends.redis.StandaloneRedisConfiguration;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.masterreplica.MasterReplica;
import io.lettuce.core.masterreplica.StatefulRedisMasterReplicaConnection;

public class RedisReactiveCommandsFactory {
    public interface CommandsFactory<T> {
        T create(RedisReactiveCommands<String, String> commands);
    }

    public interface ClusterCommandsFactory<T> {
        T create(RedisClusterReactiveCommands<String, String> commands);
    }

    private final RedisClientFactory redisClientFactory;
    private final RedisConfiguration redisConfiguration;

    @Inject
    public RedisReactiveCommandsFactory(RedisClientFactory redisClientFactory, RedisConfiguration redisConfiguration) {
        this.redisClientFactory = redisClientFactory;
        this.redisConfiguration = redisConfiguration;
    }

    public <T> T create(CommandsFactory<T> commandsFactory, ClusterCommandsFactory<T> clusterCommandsFactory) {
        AbstractRedisClient rawClient = redisClientFactory.rawRedisClient();

        return switch (redisConfiguration) {
            case StandaloneRedisConfiguration ignored ->
                commandsFactory.create(((RedisClient) rawClient).connect(StringCodec.UTF8).reactive());

            case ClusterRedisConfiguration clusterConfiguration -> {
                RedisClusterClient client = (RedisClusterClient) rawClient;
                StatefulRedisClusterConnection<String, String> connection = client.connect(StringCodec.UTF8);
                connection.setReadFrom(clusterConfiguration.readFrom());
                yield clusterCommandsFactory.create(connection.reactive());
            }

            case SentinelRedisConfiguration sentinelConfiguration -> {
                StatefulRedisMasterReplicaConnection<String, String> connection = MasterReplica.connect(
                    (RedisClient) rawClient, StringCodec.UTF8, sentinelConfiguration.redisURI());
                connection.setReadFrom(sentinelConfiguration.readFrom());
                yield commandsFactory.create(connection.reactive());
            }

            case MasterReplicaRedisConfiguration masterReplicaConfiguration -> {
                List<RedisURI> uris = RedisConfigurationUtils.asJavaRedisUris(masterReplicaConfiguration.redisURI());
                StatefulRedisMasterReplicaConnection<String, String> connection = MasterReplica.connect(
                    (RedisClient) rawClient, StringCodec.UTF8, uris);
                connection.setReadFrom(masterReplicaConfiguration.readFrom());
                yield commandsFactory.create(connection.reactive());
            }

            default ->
                throw new RuntimeException("Unknown redis configuration type: " + redisConfiguration.getClass().getName());
        };
    }
}
