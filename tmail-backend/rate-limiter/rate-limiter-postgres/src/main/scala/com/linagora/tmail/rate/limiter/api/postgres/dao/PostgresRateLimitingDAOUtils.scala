package com.linagora.tmail.rate.limiter.api.postgres.dao

import com.linagora.tmail.rate.limiter.api.LimitTypes.LimitTypes
import com.linagora.tmail.rate.limiter.api.{LimitType, LimitTypes}

import scala.jdk.CollectionConverters._

object PostgresRateLimitingDAOUtils {
  def getQuantity(limitType: LimitType): Long = {
    limitType.allowedQuantity().value.longValue
  }

  def getLimitType(limitTypeAndAllowedQuantity: java.util.Map[String, java.lang.Long]): LimitTypes = {
    val limitTypes = limitTypeAndAllowedQuantity.asScala
      .map(map => LimitType.liftOrThrow(map._1, map._2.toLong))
      .toSet
    LimitTypes.liftOrThrow(limitTypes)
  }
}
