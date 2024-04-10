package org.apache.james.events

import java.util

import io.lettuce.core.api.reactive.RedisSetReactiveCommands
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.lettuce.core.{AbstractRedisClient, RedisClient, RedisURI}
import jakarta.inject.{Inject, Singleton}
import org.apache.james.backends.redis.RedisConfiguration

import scala.jdk.CollectionConverters._

class RedisEventBusClientFactory @Singleton() @Inject()
(redisConfiguration: RedisConfiguration) {
  val rawRedisClient: AbstractRedisClient =
    if (redisConfiguration.isCluster) {
      val redisUris: util.List[RedisURI] = redisConfiguration.redisURI.value.asJava
      RedisClusterClient.create(redisUris)
    } else {
      RedisClient.create(redisConfiguration.redisURI.value.last)
    }

  def createRedisPubSubCommand(): RedisPubSubReactiveCommands[String, String] =
    if (redisConfiguration.isCluster) {
      rawRedisClient.asInstanceOf[RedisClusterClient]
        .connectPubSub().reactive()
    } else {
      rawRedisClient.asInstanceOf[RedisClient]
        .connectPubSub().reactive()
    }

  def createRedisSetCommand(): RedisSetReactiveCommands[String, String] =
    if (redisConfiguration.isCluster) {
      rawRedisClient.asInstanceOf[RedisClusterClient]
        .connect().reactive()
    } else {
      rawRedisClient.asInstanceOf[RedisClient]
        .connect().reactive()
    }
}
