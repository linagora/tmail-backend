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

package com.linagora.tmail.rate.limiter.api.cassandra

import com.google.common.base.Preconditions
import com.linagora.tmail.rate.limiter.api.cassandra.dao.{CassandraRateLimitPlanDAO, RateLimitingPlanEntry}
import com.linagora.tmail.rate.limiter.api.{RateLimitingPlan, RateLimitingPlanCreateRequest, RateLimitingPlanId, RateLimitingPlanName, RateLimitingPlanNotFoundException, RateLimitingPlanRepository, RateLimitingPlanResetRequest}
import jakarta.inject.Inject
import org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

class CassandraRateLimitingPlanRepository @Inject()(cassandraRateLimitPlanDAO: CassandraRateLimitPlanDAO) extends RateLimitingPlanRepository {

  override def create(creationRequest: RateLimitingPlanCreateRequest): Publisher[RateLimitingPlan] =
    SMono.fromCallable(() => RateLimitingPlanId.generate)
      .flatMap(planId => create(planId, RateLimitingPlanEntry.from(planId, creationRequest)))
      .flatMap(planId => SMono.fromPublisher(get(planId)))

  private def create(planId: RateLimitingPlanId, insertEntries: Seq[RateLimitingPlanEntry]): SMono[RateLimitingPlanId] =
    SFlux.fromIterable(insertEntries)
      .flatMap(insertEntry => cassandraRateLimitPlanDAO.insert(insertEntry))
      .`then`()
      .`then`(SMono.just(planId))

  override def update(resetRequest: RateLimitingPlanResetRequest): Publisher[Unit] =
    SMono.fromPublisher(cassandraRateLimitPlanDAO.planExists(resetRequest.id))
      .filter(exists => exists)
      .switchIfEmpty(SMono.error(new RateLimitingPlanNotFoundException))
      .flatMap(_ => cassandraRateLimitPlanDAO.delete(resetRequest.id))
      .`then`(create(resetRequest.id, RateLimitingPlanEntry.from(resetRequest)))
      .`then`()

  override def planExists(id: RateLimitingPlanId): Publisher[java.lang.Boolean] = cassandraRateLimitPlanDAO.planExists(id).map(boolean2Boolean)

  override def get(id: RateLimitingPlanId): Publisher[RateLimitingPlan] =
    cassandraRateLimitPlanDAO.list(id)
      .collectSeq()
      .filter(_.nonEmpty)
      .switchIfEmpty(SMono.error(new RateLimitingPlanNotFoundException))
      .map(convertEntriesToRateLimitingPlan)

  override def list(): Publisher[RateLimitingPlan] =
    cassandraRateLimitPlanDAO.list()
      .groupBy(planEntry => planEntry.planId)
      .flatMap(planEntryGroupFlux => planEntryGroupFlux.collectSeq(), DEFAULT_CONCURRENCY)
      .map(convertEntriesToRateLimitingPlan)

  private def convertEntriesToRateLimitingPlan(entries: Seq[RateLimitingPlanEntry]): RateLimitingPlan = {
    Preconditions.checkArgument(entries.nonEmpty)
    RateLimitingPlan(id = RateLimitingPlanId(entries.head.planId),
      name = RateLimitingPlanName.liftOrThrow(entries.head.planName),
      operationLimitations = entries.map(_.operationLimitations))
  }
}

