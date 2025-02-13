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
