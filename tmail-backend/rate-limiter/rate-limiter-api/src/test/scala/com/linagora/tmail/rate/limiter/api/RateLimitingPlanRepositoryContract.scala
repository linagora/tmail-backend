package com.linagora.tmail.rate.limiter.api

import com.linagora.tmail.rate.limiter.api.LimitTypes.LimitTypes
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanRepositoryContract.{CREATION_REQUEST, CREATION_REQUEST_WITH_MULTI_OPERATIONS, RESET_REQUEST}
import eu.timepit.refined.auto._
import org.apache.james.rate.limiter.api.AllowedQuantity
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.{SFlux, SMono}

import java.time.Duration
import scala.jdk.CollectionConverters._

case class TestPJ(name: String, limits: LimitTypes)

object RateLimitingPlanRepositoryContract {
  val COUNT: Count = Count(AllowedQuantity.liftOrThrow(1))
  val SIZE: Size = Size(AllowedQuantity.liftOrThrow(10000))
  val LIMIT_TYPES: LimitTypes = LimitTypes.liftOrThrow(Set(COUNT, SIZE))
  val RATE_LIMITATION: RateLimitation = RateLimitation("name1", Duration.ofMinutes(1), LIMIT_TYPES)
  val TRANSIT_LIMITS: TransitLimitations = TransitLimitations(Seq(RATE_LIMITATION))
  val RELAY_LIMITS: RelayLimitations = RelayLimitations(Seq(RATE_LIMITATION))

  val CREATION_REQUEST: RateLimitingPlanCreateRequest = RateLimitingPlanCreateRequest(
    name = "name1",
    operationLimitations = OperationLimitationsType.liftOrThrow(Seq(TRANSIT_LIMITS)))
  val RESET_REQUEST: RateLimitingPlanResetRequest = RateLimitingPlanResetRequest(
    id = RateLimitingPlanId.generate,
    name = "new name",
    operationLimitations = OperationLimitationsType.liftOrThrow(Seq(TRANSIT_LIMITS)))

  val CREATION_REQUEST_WITH_MULTI_OPERATIONS: RateLimitingPlanCreateRequest = RateLimitingPlanCreateRequest(
    name = "complex_plan",
    operationLimitations = OperationLimitationsType.liftOrThrow(Seq(
      TransitLimitations(Seq(
        RateLimitation(name = "limit1",
          period = Duration.ofMinutes(1),
          limits = LimitTypes.liftOrThrow(Set(Count(AllowedQuantity.liftOrThrow(1)), Size(AllowedQuantity.liftOrThrow(10000))))),
        RateLimitation(name = "limit2",
          period = Duration.ofMinutes(2),
          limits = LimitTypes.liftOrThrow(Set(Count(AllowedQuantity.liftOrThrow(2)), Size(AllowedQuantity.liftOrThrow(20000))))))),
      RelayLimitations(Seq(
        RateLimitation(name = "limit3",
          period = Duration.ofMinutes(3),
          limits = LimitTypes.liftOrThrow(Set(Count(AllowedQuantity.liftOrThrow(3)), Size(AllowedQuantity.liftOrThrow(30000))))),
        RateLimitation(name = "limit4",
          period = Duration.ofMinutes(4),
          limits = LimitTypes.liftOrThrow(Set(Count(AllowedQuantity.liftOrThrow(4)), Size(AllowedQuantity.liftOrThrow(40000))))))))))
}

trait RateLimitingPlanRepositoryContract {
  def testee: RateLimitingPlanRepository

  @Test
  def createShouldReturnRateLimitingPlan(): Unit = {
    val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(testee.create(CREATION_REQUEST))
      .block()

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(rateLimitingPlan.id).isNotNull
      softly.assertThat(rateLimitingPlan.operationLimitations.asJava)
        .containsExactlyInAnyOrderElementsOf(CREATION_REQUEST.operationLimitations.value.asJava)
      softly.assertThat(rateLimitingPlan.name).isEqualTo(CREATION_REQUEST.name)
    })
  }

  @Test
  def createShouldStoreEntry(): Unit = {
    val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(testee.create(CREATION_REQUEST))
      .block()

    assertThat(SMono.fromPublisher(testee.get(rateLimitingPlan.id))
      .block())
      .isEqualTo(rateLimitingPlan)
  }

  @Test
  def createShouldReturnDifferentEntry(): Unit = {
    val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(testee.create(CREATION_REQUEST))
      .block()

    assertThat(SMono.fromPublisher(testee.create(CREATION_REQUEST))
      .block())
      .isNotEqualTo(rateLimitingPlan)
  }

  @Test
  def createShouldWorkWhenHaveSeveralOperationLimitations(): Unit = {
    val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(testee.create(CREATION_REQUEST_WITH_MULTI_OPERATIONS))
      .block()

    assertThat(SFlux.fromPublisher(testee.list()).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(rateLimitingPlan)
  }

  @Test
  def updateShouldThrowWhenIdNotFound(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.update(RESET_REQUEST)).block())
      .isInstanceOf(classOf[RateLimitingPlanNotFoundException])
  }

  @Test
  def updateShouldNotThrowWhenIdExists(): Unit = {
    val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(testee.create(CREATION_REQUEST))
      .block()

    assertThatCode(() => SMono.fromPublisher(testee.update(RESET_REQUEST.copy(id = rateLimitingPlan.id))).block())
      .doesNotThrowAnyException()
  }

  @Test
  def updateShouldModifyEntry(): Unit = {
    val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(testee.create(CREATION_REQUEST))
      .block()

    SMono.fromPublisher(testee.update(RESET_REQUEST.copy(id = rateLimitingPlan.id))).block()

    assertThat(SMono.fromPublisher(testee.get(rateLimitingPlan.id)).block())
      .isEqualTo(RateLimitingPlan(
        id = rateLimitingPlan.id,
        name = RESET_REQUEST.name,
        operationLimitations = RESET_REQUEST.operationLimitations))
  }

  @Test
  def updateShouldNotModifyAnotherEntry(): Unit = {
    val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(testee.create(CREATION_REQUEST))
      .block()
    val rateLimitingPlan2: RateLimitingPlan = SMono.fromPublisher(testee.create(CREATION_REQUEST))
      .block()

    SMono.fromPublisher(testee.update(RESET_REQUEST.copy(id = rateLimitingPlan.id))).block()

    assertThat(SMono.fromPublisher(testee.get(rateLimitingPlan2.id))
      .block())
      .isEqualTo(rateLimitingPlan2)
  }

  @Test
  def getShouldThrowWhenIdNotFound(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.get(RateLimitingPlanId.generate)).block())
      .isInstanceOf(classOf[RateLimitingPlanNotFoundException])
  }

  @Test
  def getShouldReturnEntryWhenIdExists(): Unit = {
    val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(testee.create(CREATION_REQUEST))
      .block()

    assertThat(SMono.fromPublisher(testee.get(rateLimitingPlan.id)).block())
      .isEqualTo(rateLimitingPlan)
  }

  @Test
  def planExistsShouldReturnFalseByDefault(): Unit = {
    assertThat(SMono.fromPublisher(testee.planExists(RateLimitingPlanId.generate)).block())
      .isEqualTo(false)
  }

  @Test
  def planExistsShouldReturnTrueWhenPlanExists(): Unit = {
    val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(testee.create(CREATION_REQUEST))
      .block()

    assertThat(SMono.fromPublisher(testee.planExists(rateLimitingPlan.id)).block())
      .isEqualTo(true)
  }

  @Test
  def listShouldReturnEmptyByDefault(): Unit = {
    assertThat(SFlux.fromPublisher(testee.list()).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def listShouldReturnStoredEntriesWhenHasSingleElement(): Unit = {
    val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(testee.create(CREATION_REQUEST))
      .block()

    assertThat(SFlux.fromPublisher(testee.list()).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(rateLimitingPlan)
  }

  @Test
  def listShouldReturnStoredEntriesWhenHasSeveralElement(): Unit = {
    val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(testee.create(CREATION_REQUEST))
      .block()
    val rateLimitingPlan2: RateLimitingPlan = SMono.fromPublisher(testee.create(CREATION_REQUEST_WITH_MULTI_OPERATIONS))
      .block()

    assertThat(SFlux.fromPublisher(testee.list()).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(rateLimitingPlan, rateLimitingPlan2)
  }
}

class InMemoryRateLimitingPlanRepositoryTest extends RateLimitingPlanRepositoryContract {
  var inMemoryRateLimitationPlanRepository: InMemoryRateLimitingPlanRepository = _

  override def testee: RateLimitingPlanRepository = inMemoryRateLimitationPlanRepository

  @BeforeEach
  def beforeEach(): Unit = {
    inMemoryRateLimitationPlanRepository = new InMemoryRateLimitingPlanRepository();
  }
}