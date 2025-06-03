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

import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.reactive.{RedisKeyReactiveCommands, RedisSetReactiveCommands}
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.masterreplica.MasterReplica
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.lettuce.core.{AbstractRedisClient, RedisClient}
import jakarta.inject.{Inject, Singleton}
import org.apache.james.backends.redis.{MasterReplicaRedisConfiguration, RedisClientFactory, RedisConfiguration, SentinelRedisConfiguration, StandaloneRedisConfiguration}

import scala.jdk.CollectionConverters._

class RedisEventBusClientFactory @Singleton() @Inject()
(redisConfiguration: RedisConfiguration, redisClientFactory: RedisClientFactory) {
  private val rawRedisClient: AbstractRedisClient = redisClientFactory.rawRedisClient

  def createRedisPubSubCommand(): RedisPubSubReactiveCommands[String, String] = rawRedisClient match {
    case client: RedisClient => getRedisPubSubConnection(client).reactive()
    case clusterClient: RedisClusterClient => clusterClient.connectPubSub().reactive()
  }

  def createRedisSetCommand(): RedisSetReactiveCommands[String, String] = rawRedisClient match {
    case client: RedisClient => getRedisConnection(client).reactive()
    case clusterClient: RedisClusterClient => clusterClient.connect().reactive()
  }

  def createRedisKeyCommand(): RedisKeyReactiveCommands[String, String] = rawRedisClient match {
    case client: RedisClient => getRedisConnection(client).reactive()
    case clusterClient: RedisClusterClient => clusterClient.connect().reactive()
  }

  private def getRedisConnection(redisClient: RedisClient): StatefulRedisConnection[String, String] = redisConfiguration match {
    case _: StandaloneRedisConfiguration => redisClient.connect()
    case masterReplicaRedisConfiguration: MasterReplicaRedisConfiguration => MasterReplica.connect(redisClient,
      StringCodec.UTF8,
      masterReplicaRedisConfiguration.redisURI.value.asJava)
    case sentinelRedisConfiguration: SentinelRedisConfiguration => MasterReplica.connect(redisClient,
      StringCodec.UTF8,
      sentinelRedisConfiguration.redisURI)
  }

  private def getRedisPubSubConnection(redisClient: RedisClient): StatefulRedisPubSubConnection[String, String] = redisConfiguration match {
    case _: StandaloneRedisConfiguration => redisClient.connectPubSub()
    case masterReplicaRedisConfiguration: MasterReplicaRedisConfiguration => redisClient.connectPubSub(masterReplicaRedisConfiguration.redisURI.value.head)
    case sentinelRedisConfiguration: SentinelRedisConfiguration => redisClient.connectPubSub(sentinelRedisConfiguration.redisURI)
  }
}
