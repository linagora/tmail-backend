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

package com.linagora.tmail.rate.limiter.api

import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.collection.concurrent.Map

trait RateLimitingPlanRepository {

  def create(creationRequest: RateLimitingPlanCreateRequest): Publisher[RateLimitingPlan]

  def update(resetRequest: RateLimitingPlanResetRequest): Publisher[Unit]

  def get(id: RateLimitingPlanId): Publisher[RateLimitingPlan]

  def planExists(id: RateLimitingPlanId): Publisher[java.lang.Boolean]

  def list(): Publisher[RateLimitingPlan]
}

case class RateLimitingPlanNotFoundException() extends RuntimeException

class InMemoryRateLimitingPlanRepository() extends RateLimitingPlanRepository {

  val rateLimitingPlanStore: Map[RateLimitingPlanId, RateLimitingPlan] = scala.collection.concurrent.TrieMap()

  override def create(creationRequest: RateLimitingPlanCreateRequest): Publisher[RateLimitingPlan] =
    SMono.fromCallable(() => {
      val rateLimitingPlan: RateLimitingPlan = RateLimitingPlan.from(creationRequest)
      rateLimitingPlanStore.put(rateLimitingPlan.id, rateLimitingPlan)
      rateLimitingPlan
    })

  override def update(resetRequest: RateLimitingPlanResetRequest): Publisher[Unit] =
    SMono.justOrEmpty(rateLimitingPlanStore.get(resetRequest.id))
      .switchIfEmpty(SMono.error(new RateLimitingPlanNotFoundException))
      .map(rateLimitingPlan => rateLimitingPlanStore.update(resetRequest.id, RateLimitingPlan.update(rateLimitingPlan, resetRequest)))

  override def get(id: RateLimitingPlanId): Publisher[RateLimitingPlan] =
    SMono.justOrEmpty(rateLimitingPlanStore.get(id))
      .switchIfEmpty(SMono.error(new RateLimitingPlanNotFoundException))


  override def list(): Publisher[RateLimitingPlan] =
    SFlux.fromIterable(rateLimitingPlanStore)
      .map(_._2)

  override def planExists(id: RateLimitingPlanId): Publisher[java.lang.Boolean] = SMono.just(rateLimitingPlanStore.contains(id))
}