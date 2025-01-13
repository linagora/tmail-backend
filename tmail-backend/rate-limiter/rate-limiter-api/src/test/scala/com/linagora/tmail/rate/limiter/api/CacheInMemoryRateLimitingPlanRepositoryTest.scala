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

import java.time.Duration

import org.apache.james.metrics.api.NoopGaugeRegistry
import org.junit.jupiter.api.BeforeEach

class CacheInMemoryRateLimitingPlanRepositoryTest extends RateLimitingPlanRepositoryContract {
  var repository: RateLimitingPlanRepository = _

  override def testee: RateLimitingPlanRepository = repository

  @BeforeEach
  def beforeEach(): Unit = {
    val inMemoryRepository: RateLimitingPlanRepository = new InMemoryRateLimitingPlanRepository()
    repository = new CacheRateLimitingPlan(inMemoryRepository, Duration.ofMinutes(2), new NoopGaugeRegistry)
  }
}