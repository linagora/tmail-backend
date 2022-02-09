package com.linagora.tmail.rate.limiter.api

import com.linagora.tmail.rate.limiter.api.LimitTypes.LimitTypes
import com.linagora.tmail.rate.limiter.api.RateLimitationPlanRepositoryContract.{CREATION_REQUEST, RELAY_LIMITS, RESET_REQUEST, TRANSIT_LIMITS}
import org.apache.james.rate.limiter.api.AllowedQuantity
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.{SFlux, SMono}

import java.time.Duration
import scala.jdk.CollectionConverters._

case class TestPJ(name: String, limits: LimitTypes)

object RateLimitationPlanRepositoryContract {
  val COUNT: Count = Count(AllowedQuantity.liftOrThrow(1))
  val SIZE: Size = Size(AllowedQuantity.liftOrThrow(10000))
  val LIMIT_TYPES: LimitTypes = LimitTypes.liftOrThrow(Set(COUNT, SIZE))
  val RATE_LIMITATION: RateLimitation = RateLimitation("name1", Duration.ofMinutes(1), LIMIT_TYPES)
  val TRANSIT_LIMITS: TransitLimitations = TransitLimitations(Seq(RATE_LIMITATION))
  val RELAY_LIMITS: RelayLimitations = RelayLimitations(Seq(RATE_LIMITATION))

  val CREATION_REQUEST: RateLimitingPlanCreateRequest = RateLimitingPlanCreateRequest(
    name = RateLimitingPlanName("name1"),
    operationLimitations = Seq(TRANSIT_LIMITS))
  val RESET_REQUEST: RateLimitingPlanResetRequest = RateLimitingPlanResetRequest(
    id = RateLimitingPlanId.generate,
    name = RateLimitingPlanName("new name"),
    operationLimitations = Seq(TRANSIT_LIMITS))
}

trait RateLimitationPlanRepositoryContract {
  def testee: RateLimitationPlanRepository

  @Test
  def createShouldReturnRateLimitingPlan(): Unit = {
    val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(testee.create(CREATION_REQUEST))
      .block()

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(rateLimitingPlan.id).isNotNull
      softly.assertThat(rateLimitingPlan.operationLimitations.asJava).containsExactlyInAnyOrderElementsOf(CREATION_REQUEST.operationLimitations.asJava)
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
  def updateShouldThrowWhenIdNotFound(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.update(RESET_REQUEST)).block())
      .isInstanceOf(classOf[RateLimitingPlanNotFoundException])
  }

  @Test
  def updateShouldReturnRateLimitingPlan(): Unit = {
    val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(testee.create(CREATION_REQUEST))
      .block()

    val updatedResult: RateLimitingPlan = SMono.fromPublisher(testee.update(RESET_REQUEST.copy(id = rateLimitingPlan.id))).block()

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(updatedResult.id).isEqualTo(rateLimitingPlan.id)
      softly.assertThat(updatedResult.operationLimitations.asJava).containsExactlyInAnyOrderElementsOf(RESET_REQUEST.operationLimitations.asJava)
      softly.assertThat(updatedResult.name).isEqualTo(RESET_REQUEST.name)
    })
  }

  @Test
  def updateShouldModifyEntry(): Unit = {
    val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(testee.create(CREATION_REQUEST))
      .block()

    val updatedResult: RateLimitingPlan = SMono.fromPublisher(testee.update(RESET_REQUEST.copy(id = rateLimitingPlan.id))).block()

    assertThat(SMono.fromPublisher(testee.get(rateLimitingPlan.id))
      .block())
      .isEqualTo(updatedResult)
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
    val rateLimitingPlan2: RateLimitingPlan = SMono.fromPublisher(testee.create(CREATION_REQUEST.copy(name = RateLimitingPlanName("name2"))))
      .block()

    assertThat(SFlux.fromPublisher(testee.list()).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(rateLimitingPlan, rateLimitingPlan2)
  }
}

class InMemoryRateLimitationPlanRepositoryTest extends RateLimitationPlanRepositoryContract {
  var inMemoryRateLimitationPlanRepository: InMemoryRateLimitationPlanRepository = _

  override def testee: RateLimitationPlanRepository = inMemoryRateLimitationPlanRepository

  @BeforeEach
  def beforeEach(): Unit = {
    inMemoryRateLimitationPlanRepository = new InMemoryRateLimitationPlanRepository();
  }
}