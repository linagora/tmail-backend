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

package org.apache.james.events

import io.lettuce.core.api.reactive.{RedisKeyReactiveCommands, RedisSetReactiveCommands}
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.lettuce.core.{AbstractRedisClient, RedisClient}
import jakarta.annotation.PreDestroy
import jakarta.inject.{Inject, Singleton}
import org.apache.james.backends.redis.{ClusterRedisConfiguration, MasterReplicaRedisConfiguration, RedisClientFactory, RedisConfiguration, SentinelRedisConfiguration, StandaloneRedisConfiguration}

class RedisEventBusClientFactory @Singleton() @Inject()
(redisConfiguration: RedisConfiguration, redisClientFactory: RedisClientFactory) {
  val rawRedisClient: AbstractRedisClient = redisConfiguration match {
    case standaloneConfiguration: StandaloneRedisConfiguration => redisClientFactory.createStandaloneClient(standaloneConfiguration)
    case masterReplicaRedisConfiguration: MasterReplicaRedisConfiguration =>
      redisClientFactory.createStandaloneClient(new StandaloneRedisConfiguration(masterReplicaRedisConfiguration.redisURI.value.last,
        masterReplicaRedisConfiguration.useSSL, masterReplicaRedisConfiguration.mayBeSSLConfiguration, masterReplicaRedisConfiguration.ioThreads, masterReplicaRedisConfiguration.workerThreads))
    case clusterRedisConfiguration: ClusterRedisConfiguration => redisClientFactory.createClusterClient(clusterRedisConfiguration)
    case sentinelRedisConfiguration: SentinelRedisConfiguration =>
      redisClientFactory.createStandaloneClient(new StandaloneRedisConfiguration(sentinelRedisConfiguration.redisURI,
        sentinelRedisConfiguration.useSSL, sentinelRedisConfiguration.mayBeSSLConfiguration, sentinelRedisConfiguration.ioThreads, sentinelRedisConfiguration.workerThreads))
  }

  def createRedisPubSubCommand(): RedisPubSubReactiveCommands[String, String] = rawRedisClient match {
    case client: RedisClient => client.connectPubSub().reactive()
    case clusterClient: RedisClusterClient => clusterClient.connectPubSub().reactive()
  }

  def createRedisSetCommand(): RedisSetReactiveCommands[String, String] = rawRedisClient match {
    case client: RedisClient => client.connect().reactive()
    case clusterClient: RedisClusterClient => clusterClient.connect().reactive()
  }

  def createRedisKeyCommand(): RedisKeyReactiveCommands[String, String] = rawRedisClient match {
    case client: RedisClient => client.connect().reactive()
    case clusterClient: RedisClusterClient => clusterClient.connect().reactive()
  }

  @PreDestroy
  def close(): Unit = {
    rawRedisClient.close()
  }
}
