package com.linagora.tmail.rate.limiter.api.postgres.dao

import com.linagora.tmail.rate.limiter.api.LimitType

object PostgresRateLimitingDAOUtils {
  def getQuantity(limitType: LimitType): Long = {
    limitType.allowedQuantity().value.longValue
  }
}
