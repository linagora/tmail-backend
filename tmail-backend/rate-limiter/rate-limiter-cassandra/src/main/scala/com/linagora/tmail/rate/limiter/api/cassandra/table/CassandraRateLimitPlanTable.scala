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

package com.linagora.tmail.rate.limiter.api.cassandra.table

import com.datastax.oss.driver.api.core.`type`.DataTypes
import org.apache.james.backends.cassandra.components.CassandraModule

object CassandraRateLimitPlanTable {
  val TABLE_NAME: String = "rate_limit_plan"
  val PLAN_ID: String = "plan_id"
  val PLAN_NAME: String = "plan_name"
  val OPERATION_LIMITATION_NAME: String = "operation_limitation_name"
  val RATE_LIMITATIONS: String = "rate_limitations"

  val MODULE: CassandraModule = CassandraModule.table(TABLE_NAME)
    .comment("Hold Rate Limiting Plan - Use to management.")
    .statement(statement => types => statement
      .withPartitionKey(PLAN_ID, DataTypes.UUID)
      .withStaticColumn(PLAN_NAME, DataTypes.TEXT)
      .withClusteringColumn(OPERATION_LIMITATION_NAME, DataTypes.TEXT)
      .withColumn(RATE_LIMITATIONS,
        DataTypes.frozenListOf(DataTypes.tupleOf(
          DataTypes.TEXT,
          DataTypes.BIGINT,
          DataTypes.frozenMapOf(DataTypes.TEXT,  DataTypes.BIGINT)))))
    .build
}

object CassandraRateLimitPlanHeaderEntry {
  val RATE_LIMITATION_NAME_INDEX: Int = 0
  val RATE_LIMITATION_DURATION_INDEX: Int = 1
  val RATE_LIMITS_INDEX: Int = 2
}
