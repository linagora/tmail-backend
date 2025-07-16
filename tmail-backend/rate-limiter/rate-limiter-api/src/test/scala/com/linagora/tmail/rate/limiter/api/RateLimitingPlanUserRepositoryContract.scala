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

import java.util.UUID

import com.linagora.tmail.rate.limiter.api.RateLimitingPlanUserRepositoryContract.{ALICE, BOB, PLAN_ID_1, PLAN_ID_2}
import org.apache.james.core.Username
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

object RateLimitingPlanUserRepositoryContract {
  val BOB: Username = Username.of("BOB@domain1.tld")
  val ALICE: Username = Username.of("ALICE@domain2.tld")
  val PLAN_ID_1: RateLimitingPlanId = RateLimitingPlanId(UUID.randomUUID())
  val PLAN_ID_2: RateLimitingPlanId = RateLimitingPlanId(UUID.randomUUID())
}

trait RateLimitingPlanUserRepositoryContract {
  def testee: RateLimitingPlanUserRepository

  @Test
  def applyPlanShouldSucceed(): Unit = {
    SMono.fromPublisher(testee.applyPlan(BOB, PLAN_ID_1)).block()

    assertThat(SMono.fromPublisher(testee.getPlanByUser(BOB)).block()).isEqualTo(PLAN_ID_1)
  }

  @Test
  def applyPlanWhenManyUsersHaveSamePlanShouldSucceed(): Unit = {
    SMono.fromPublisher(testee.applyPlan(BOB, PLAN_ID_1)).block()
    SMono.fromPublisher(testee.applyPlan(ALICE, PLAN_ID_1)).block()

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(SMono.fromPublisher(testee.getPlanByUser(BOB)).block().value).isEqualTo(PLAN_ID_1.value)
      softly.assertThat(SMono.fromPublisher(testee.getPlanByUser(ALICE)).block().value).isEqualTo(PLAN_ID_1.value)
    })
  }

  @Test
  def applyPlanShouldOverridePreviousPlan(): Unit = {
    SMono.fromPublisher(testee.applyPlan(BOB, PLAN_ID_1)).block()
    SMono.fromPublisher(testee.applyPlan(BOB, PLAN_ID_2)).block()

    assertThat(SMono.fromPublisher(testee.getPlanByUser(BOB)).block()).isEqualTo(PLAN_ID_2)
  }

  @Test
  def applyPlanShouldThrowWhenUsernameIsNull(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.applyPlan(null, PLAN_ID_1)).block())
      .isInstanceOf(classOf[NullPointerException])
  }

  @Test
  def applyPlanShouldThrowWhenPlanIdIsNull(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.applyPlan(BOB, null)).block())
      .isInstanceOf(classOf[NullPointerException])
  }

  @Test
  def revokePlanShouldCleanTheRecord(): Unit = {
    SMono.fromPublisher(testee.applyPlan(BOB, PLAN_ID_1)).block()
    SMono.fromPublisher(testee.revokePlan(BOB)).block()

    assertThatThrownBy(() => SMono.fromPublisher(testee.getPlanByUser(BOB)).block())
      .isInstanceOf(classOf[RateLimitingPlanNotFoundException])
  }

  @Test
  def insertSettingsAfterDeletingSettingsShouldWorkWell(): Unit = {
    // This test simulates a scenario where the user record exists but has no settings yet.
    SMono.fromPublisher(testee.applyPlan(BOB, PLAN_ID_1)).block()
    SMono.fromPublisher(testee.revokePlan(BOB)).block()

    SMono.fromPublisher(testee.applyPlan(BOB, PLAN_ID_2)).block()

    assertThat(SMono.fromPublisher(testee.getPlanByUser(BOB)).block()).isEqualTo(PLAN_ID_2)
  }

  @Test
  def revokePlanShouldThrowWhenUsernameIsNull(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.revokePlan(null)).block())
      .isInstanceOf(classOf[NullPointerException])
  }

  @Test
  def getPlanByUserShouldSucceed(): Unit = {
    SMono.fromPublisher(testee.applyPlan(BOB, PLAN_ID_1)).block()
    SMono.fromPublisher(testee.applyPlan(ALICE, PLAN_ID_2)).block()

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(SMono.fromPublisher(testee.getPlanByUser(BOB)).block().value).isEqualTo(PLAN_ID_1.value)
      softly.assertThat(SMono.fromPublisher(testee.getPlanByUser(ALICE)).block().value).isEqualTo(PLAN_ID_2.value)
    })
  }

  @Test
  def getPlanByUserWhenNotFoundShouldThrowException(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.getPlanByUser(BOB)).block())
      .isInstanceOf(classOf[RateLimitingPlanNotFoundException])
  }

  @Test
  def getPlanByUserShouldThrowWhenUsernameIsNull(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.getPlanByUser(null)).block())
      .isInstanceOf(classOf[NullPointerException])
  }

  @Test
  def listUserShouldSucceed(): Unit = {
    SMono.fromPublisher(testee.applyPlan(BOB, PLAN_ID_1)).block()
    SMono.fromPublisher(testee.applyPlan(ALICE, PLAN_ID_1)).block()

    assertThat(SFlux.fromPublisher(testee.listUsers(PLAN_ID_1)).collectSeq().block().asJava).containsExactlyInAnyOrder(BOB, ALICE)
  }

  @Test
  def listUserShouldReturnEmptyByDefault(): Unit = {
    assertThat(SFlux.fromPublisher(testee.listUsers(PLAN_ID_1)).collectSeq().block().asJava).isEmpty()
  }

  @Test
  def listUserShouldThrowWhenPlanIdIsNull(): Unit = {
    assertThatThrownBy(() => SFlux.fromPublisher(testee.listUsers(null)).collectSeq().block())
      .isInstanceOf(classOf[NullPointerException])
  }
}
