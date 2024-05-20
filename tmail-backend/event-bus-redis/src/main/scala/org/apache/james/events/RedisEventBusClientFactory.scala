package org.apache.james.events

import io.lettuce.core.api.reactive.{RedisKeyReactiveCommands, RedisSetReactiveCommands}
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.lettuce.core.{AbstractRedisClient, RedisClient}
import jakarta.inject.{Inject, Singleton}
import org.apache.james.backends.redis.{ClusterRedisConfiguration, MasterReplicaRedisConfiguration, RedisConfiguration, StandaloneRedisConfiguration}

import scala.jdk.CollectionConverters._

class RedisEventBusClientFactory @Singleton() @Inject()
(redisConfiguration: RedisConfiguration) {
  val rawRedisClient: AbstractRedisClient = redisConfiguration match {
    case standaloneConfiguration: StandaloneRedisConfiguration => RedisClient.create(standaloneConfiguration.redisURI)
    case masterReplicaRedisConfiguration: MasterReplicaRedisConfiguration => RedisClient.create(masterReplicaRedisConfiguration.redisURI.value.last)
    case clusterRedisConfiguration: ClusterRedisConfiguration => RedisClusterClient.create(clusterRedisConfiguration.redisURI.value.asJava)
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
}
