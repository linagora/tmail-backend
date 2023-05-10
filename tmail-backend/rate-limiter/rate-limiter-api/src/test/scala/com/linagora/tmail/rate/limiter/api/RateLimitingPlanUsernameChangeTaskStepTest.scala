package com.linagora.tmail.rate.limiter.api

import com.linagora.tmail.rate.limiter.api.LimitTypes.LimitTypes
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanUserRepositoryContract.{ALICE, BOB}
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanUsernameChangeTaskStepTest.{CREATION_REQUEST, CREATION_REQUEST_2}
import com.linagora.tmail.rate.limiter.api.memory.MemoryRateLimitingPlanUserRepository
import org.apache.james.rate.limiter.api.AllowedQuantity
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.{SFlux, SMono}
import eu.timepit.refined.auto._

import java.time.Duration

object RateLimitingPlanUsernameChangeTaskStepTest {
  val COUNT: Count = Count(AllowedQuantity.validate(1).toOption.get)
  val SIZE: Size = Size(AllowedQuantity.validate(10000).toOption.get)
  val LIMIT_TYPES: LimitTypes = LimitTypes.liftOrThrow(Set(COUNT, SIZE))
  val RATE_LIMITATION: RateLimitation = RateLimitation("name1", Duration.ofMinutes(1), LIMIT_TYPES)
  val TRANSIT_LIMITS: TransitLimitations = TransitLimitations(Seq(RATE_LIMITATION))
  val RELAY_LIMITS: RelayLimitations = RelayLimitations(Seq(RATE_LIMITATION))
  val CREATION_REQUEST: RateLimitingPlanCreateRequest = RateLimitingPlanCreateRequest(
    name = "plan1",
    operationLimitations = OperationLimitationsType.liftOrThrow(Seq(TRANSIT_LIMITS)))
  val CREATION_REQUEST_2: RateLimitingPlanCreateRequest = RateLimitingPlanCreateRequest(
    name = "plan2",
    operationLimitations = OperationLimitationsType.liftOrThrow(Seq(TRANSIT_LIMITS)))
}

class RateLimitingPlanUsernameChangeTaskStepTest {

  var rateLimitingPlanRepository: RateLimitingPlanRepository = _
  var rateLimitingPlanUserRepository: RateLimitingPlanUserRepository = _
  var testee: RateLimitingPlanUsernameChangeTaskStep = _

  @BeforeEach
  def beforeEach(): Unit = {
    rateLimitingPlanRepository = new InMemoryRateLimitingPlanRepository()
    rateLimitingPlanUserRepository = new MemoryRateLimitingPlanUserRepository()
    testee = new RateLimitingPlanUsernameChangeTaskStep(rateLimitingPlanUserRepository)
  }

  @Test
  def shouldMigrateRateLimitingPlans(): Unit = {
    val planId: RateLimitingPlanId = SMono.fromPublisher(rateLimitingPlanRepository.create(CREATION_REQUEST))
      .map(_.id)
      .block()
    SMono.fromPublisher(rateLimitingPlanUserRepository.applyPlan(ALICE, planId)).block()

    SMono.fromPublisher(testee.changeUsername(ALICE, BOB)).block()

    assertThat(SMono.fromPublisher(rateLimitingPlanUserRepository.getPlanByUser(BOB)).block())
      .isEqualTo(planId)
  }

  @Test
  def shouldRevokeOldUserRateLimitingPlans(): Unit = {
    val planId: RateLimitingPlanId = SMono.fromPublisher(rateLimitingPlanRepository.create(CREATION_REQUEST))
      .map(_.id)
      .block()
    SMono.fromPublisher(rateLimitingPlanUserRepository.applyPlan(ALICE, planId)).block()

    SMono.fromPublisher(testee.changeUsername(ALICE, BOB)).block()

    assertThatThrownBy(() => SMono.fromPublisher(rateLimitingPlanUserRepository.getPlanByUser(ALICE)).block())
      .isInstanceOf(classOf[RateLimitingPlanNotFoundException])
  }

  @Test
  def shouldNotOverrideNewUserRateLimitingPlans(): Unit = {
    val planId: RateLimitingPlanId = SMono.fromPublisher(rateLimitingPlanRepository.create(CREATION_REQUEST))
      .map(_.id)
      .block()
    SMono.fromPublisher(rateLimitingPlanUserRepository.applyPlan(ALICE, planId)).block()

    val planId2: RateLimitingPlanId = SMono.fromPublisher(rateLimitingPlanRepository.create(CREATION_REQUEST_2))
      .map(_.id)
      .block()
    SMono.fromPublisher(rateLimitingPlanUserRepository.applyPlan(BOB, planId2)).block()

    SMono.fromPublisher(testee.changeUsername(ALICE, BOB)).block()

    assertThat(SMono.fromPublisher(rateLimitingPlanUserRepository.getPlanByUser(BOB)).block())
      .isEqualTo(planId2)
    assertThatThrownBy(() => SMono.fromPublisher(rateLimitingPlanUserRepository.getPlanByUser(ALICE)).block())
      .isInstanceOf(classOf[RateLimitingPlanNotFoundException])
  }

  @Test
  def migrateShouldSucceedWhenOldUserHasNoPlan(): Unit = {
    SMono.fromPublisher(testee.changeUsername(ALICE, BOB)).block()

    assertThatThrownBy(() => SMono.fromPublisher(rateLimitingPlanUserRepository.getPlanByUser(ALICE)).block())
      .isInstanceOf(classOf[RateLimitingPlanNotFoundException])

    assertThatThrownBy(() => SMono.fromPublisher(rateLimitingPlanUserRepository.getPlanByUser(BOB)).block())
      .isInstanceOf(classOf[RateLimitingPlanNotFoundException])
  }

}
