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
import org.apache.james.backends.cassandra.components.CassandraDataDefinition

object CassandraRateLimitPlanUserTable {
  val TABLE_NAME = "rate_limit_plan_user"
  val USERNAME = "username"
  val PLAN_ID = "plan_id"

  val MODULE: CassandraDataDefinition = CassandraDataDefinition
    .table(TABLE_NAME)
    .comment("Hold User - Rate limiting plan mapping data. Use to manage user plan.")
    .statement(statement => types => statement
      .withPartitionKey(USERNAME, DataTypes.TEXT)
      .withColumn(PLAN_ID, DataTypes.UUID))
    .build
}
