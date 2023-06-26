package com.linagora.tmail.james.jmap.label

import java.time.ZonedDateTime
import java.util.stream.IntStream

import com.linagora.tmail.james.jmap.label.LabelChangeRepositoryContract.DATE
import com.linagora.tmail.james.jmap.model.LabelId
import org.apache.james.core.Username
import org.apache.james.jmap.api.change.{Limit, State}
import org.apache.james.jmap.api.exception.ChangeNotFoundException
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.utils.UpdatableTickingClock
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._

object LabelChangeRepositoryContract {
  val ACCOUNT_ID: AccountId = AccountId.fromUsername(Username.of("bob"))
  val DATE: ZonedDateTime = ZonedDateTime.now
  val DEFAULT_NUMBER_OF_CHANGES: Limit = Limit.of(5)
  val labelChangeFunc: State.Factory => LabelChange = stateFactory => LabelChange(
    accountId = ACCOUNT_ID,
    created = Set(LabelId.generate()),
    updated = Set(),
    destroyed = Set(),
    state = stateFactory.generate())
}

trait LabelChangeRepositoryContract {

  import LabelChangeRepositoryContract._

  def testee: LabelChangeRepository

  def stateFactory: State.Factory

  def setClock(newTime: ZonedDateTime): Unit

  @Test
  def saveChangeShouldSuccess(): Unit = {
    val labelChange: LabelChange = labelChangeFunc(stateFactory)
    assertThatCode(() => SMono(testee.save(labelChange)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def getLatestStateShouldReturnInitialWhenEmpty(): Unit = {
    assertThat(SMono(testee.getLatestState(ACCOUNT_ID)).block())
      .isEqualTo(State.INITIAL)
  }

  @Test
  def getLatestStateShouldReturnLastPersistedState(): Unit = {
    val labelChange1: LabelChange = labelChangeFunc(stateFactory)
    SMono(testee.save(labelChange1)).block()

    setClock(DATE.plusHours(1))

    val labelChange2: LabelChange = labelChangeFunc(stateFactory)
    SMono(testee.save(labelChange2)).block()

    setClock(DATE.plusHours(2))

    val labelChangeLatest: LabelChange = labelChangeFunc(stateFactory)
    SMono(testee.save(labelChangeLatest)).block()

    assertThat(SMono(testee.getLatestState(ACCOUNT_ID)).block())
      .isEqualTo(labelChangeLatest.state)
  }

  @Test
  def getChangesShouldSuccess(): Unit = {
    val labelId1: LabelId = LabelId.generate()
    val labelId2: LabelId = LabelId.generate()

    val reference: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId1))
    val labelChange1: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(),
        updated = Set(labelId2))

    SMono(testee.save(reference)).block()
    setClock(DATE.plusHours(1))
    SMono(testee.save(labelChange1)).block()
    assertThat(SMono(testee.getSinceState(ACCOUNT_ID, reference.state)).block()
      .getAllChanges.asJava)
      .hasSameElementsAs(labelChange1.updated.asJava)
  }

  @Test
  def getChangesShouldReturnCurrentStateWhenNoNewerState(): Unit = {
    val labelChange: LabelChange = labelChangeFunc(stateFactory)
    SMono(testee.save(labelChange)).block()
    assertThat(SMono(testee.getSinceState(ACCOUNT_ID, labelChange.state)).block()
      .newState).isEqualTo(labelChange.state)
  }

  @Test
  def getChangesShouldReturnOnlyLimitedChangesAndTheOldestOnes(): Unit = {
    val labelId1: LabelId = LabelId.generate()
    val labelId2: LabelId = LabelId.generate()
    val labelId3: LabelId = LabelId.generate()
    val labelId4: LabelId = LabelId.generate()

    val labelChange: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId1))
    val labelChange2: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId2))
    val labelChange3: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId3))
    val labelChange4: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId4))

    SMono(testee.save(labelChange)).block()
    setClock(DATE.plusHours(1))
    SMono(testee.save(labelChange2)).block()
    setClock(DATE.plusHours(2))
    SMono(testee.save(labelChange3)).block()
    setClock(DATE.plusHours(3))
    SMono(testee.save(labelChange4)).block()

    assertThat(SMono(testee.getSinceState(ACCOUNT_ID, labelChange.state, Some(Limit.of(2)))).block()
      .created.asJava)
      .containsExactlyInAnyOrder(labelId2, labelId3)
  }

  @Test
  def getChangesShouldReturnAllFromInitial(): Unit = {
    val labelId1: LabelId = LabelId.generate()
    val labelId2: LabelId = LabelId.generate()
    val labelId3: LabelId = LabelId.generate()
    val labelId4: LabelId = LabelId.generate()

    val labelChange: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId1))
    val labelChange2: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId2))
    val labelChange3: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId3))
    val labelChange4: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId4))

    SMono(testee.save(labelChange)).block()
    setClock(DATE.plusHours(1))
    SMono(testee.save(labelChange2)).block()
    setClock(DATE.plusHours(2))
    SMono(testee.save(labelChange3)).block()
    setClock(DATE.plusHours(3))
    SMono(testee.save(labelChange4)).block()

    assertThat(SMono(testee.getSinceState(ACCOUNT_ID, State.INITIAL, Some(Limit.of(3)))).block()
      .created.asJava)
      .containsExactlyInAnyOrder(labelId1, labelId2, labelId3)
  }

  @Test
  def getChangesShouldLimitChangesWhenMaxChangesOmitted(): Unit = {
    val labelId1: LabelId = LabelId.generate()
    val reference: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId1))
      SMono(testee.save(reference)).block()

    IntStream.range(0, 300)
      .forEach(i => {
        setClock(DATE.plusHours(i + 1))
        val labelChange: LabelChange = labelChangeFunc(stateFactory)
          .copy(created = Set(LabelId.generate()))
        SMono(testee.save(labelChange)).block()
      })

    assertThat(SMono(testee.getSinceState(ACCOUNT_ID, reference.state, None)).block()
      .getAllChanges.size)
      .isEqualTo(256)
  }

  @Test
  def getChangesShouldNotReturnMoreThanMaxChanges(): Unit = {
    val labelId1: LabelId = LabelId.generate()
    val labelId2: LabelId = LabelId.generate()
    val labelId3: LabelId = LabelId.generate()
    val labelId4: LabelId = LabelId.generate()
    val labelId5: LabelId = LabelId.generate()

    val reference: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId1))
    val labelChange1: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId2, labelId3))
    val labelChange2: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId4, labelId5))

    SMono(testee.save(reference)).block()
    setClock(DATE.plusHours(1))
    SMono(testee.save(labelChange1)).block()
    setClock(DATE.plusHours(2))
    SMono(testee.save(labelChange2)).block()

    assertThat(SMono(testee.getSinceState(ACCOUNT_ID, reference.state,
      Some(Limit.of(3)))).block()
      .getAllChanges.asJava)
      .hasSameElementsAs(labelChange1.created.asJava)
  }

  @Test
  def getChangesShouldReturnNewState(): Unit = {
    val labelChange: LabelChange = labelChangeFunc(stateFactory)
    val labelChange2: LabelChange = labelChangeFunc(stateFactory)
    val labelChange3: LabelChange = labelChangeFunc(stateFactory)

    SMono(testee.save(labelChange)).block()
    setClock(DATE.plusHours(1))
    SMono(testee.save(labelChange2)).block()
    setClock(DATE.plusHours(2))
    SMono(testee.save(labelChange3)).block()
    assertThat(SMono(testee.getSinceState(ACCOUNT_ID, labelChange.state)).block()
      .newState)
      .isEqualTo(labelChange3.state)
  }

  @Test
  def hasMoreChangesShouldBeTrueWhenMoreChanges(): Unit = {
    val labelId1: LabelId = LabelId.generate()
    val labelId2: LabelId = LabelId.generate()
    val labelId3: LabelId = LabelId.generate()

    val labelChange: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId1))
    val labelChange2: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId2, labelId3))
    val labelChange3: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(),
        updated = Set(labelId1, labelId2))

    SMono(testee.save(labelChange)).block()
    setClock(DATE.plusHours(1))
    SMono(testee.save(labelChange2)).block()
    setClock(DATE.plusHours(2))
    SMono(testee.save(labelChange3)).block()
    assertThat(SMono(testee.getSinceState(ACCOUNT_ID, labelChange.state, Some(Limit.of(2)))).block()
      .hasMoreChanges)
      .isTrue()
  }

  @Test
  def hasMoreChangesShouldBeFalseWhenNoMoreChanges(): Unit = {
    val labelId1: LabelId = LabelId.generate()
    val labelId2: LabelId = LabelId.generate()
    val labelId3: LabelId = LabelId.generate()

    val reference: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId1))
    val labelChange1: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId2, labelId3))
    val labelChange2: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(),
        updated = Set(labelId2, labelId3))
    SMono(testee.save(reference)).block()
    setClock(DATE.plusHours(1))
    SMono(testee.save(labelChange1)).block()
    setClock(DATE.plusHours(2))
    SMono(testee.save(labelChange2)).block()

    assertThat(SMono(testee.getSinceState(ACCOUNT_ID, reference.state,
      Some(Limit.of(4)))).block().hasMoreChanges)
      .isFalse
  }

  @Test
  def changesShouldBeStoredInTheirRespectiveType(): Unit = {
    val labelId1: LabelId = LabelId.generate()
    val labelId2: LabelId = LabelId.generate()
    val labelId3: LabelId = LabelId.generate()
    val labelId4: LabelId = LabelId.generate()
    val labelId5: LabelId = LabelId.generate()
    val labelId6: LabelId = LabelId.generate()
    val labelId7: LabelId = LabelId.generate()
    val labelId8: LabelId = LabelId.generate()
    val labelId9: LabelId = LabelId.generate()
    val labelId10: LabelId = LabelId.generate()

    val reference: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId1))
    val labelChange1: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId2, labelId3, labelId4, labelId5))
    val labelChange2: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId6, labelId7),
        updated = Set(labelId2, labelId3, labelId9),
        destroyed = Set(labelId4))
    val labelChange3: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId8),
        updated = Set(labelId6, labelId7),
        destroyed = Set(labelId5, labelId10))

    SMono(testee.save(reference)).block()
    setClock(DATE.plusHours(1))
    SMono(testee.save(labelChange1)).block()
    setClock(DATE.plusHours(2))
    SMono(testee.save(labelChange2)).block()
    setClock(DATE.plusHours(3))
    SMono(testee.save(labelChange3)).block()

    val labelChanges: LabelChanges = SMono(testee.getSinceState(ACCOUNT_ID, reference.state,
      Some(Limit.of(20)))).block()
    assertSoftly(softly => {
      softly.assertThat(labelChanges.created.asJava).containsExactlyInAnyOrder(labelId2, labelId3, labelId6, labelId7, labelId8)
      softly.assertThat(labelChanges.updated.asJava).containsExactlyInAnyOrder(labelId9)
      softly.assertThat(labelChanges.destroyed.asJava).containsExactlyInAnyOrder(labelId10)
    })
  }

  @Test
  def getChangesShouldIgnoreDuplicatedValues(): Unit = {
    val labelId1: LabelId = LabelId.generate()
    val labelId2: LabelId = LabelId.generate()
    val labelId3: LabelId = LabelId.generate()
    val labelChange1: LabelChange = labelChangeFunc(stateFactory)
      .copy(created = Set(labelId1))
    val labelChange2: LabelChange = labelChangeFunc(stateFactory)
      .copy(updated = Set(labelId1, labelId2), created = Set())

    val labelChange3: LabelChange = labelChangeFunc(stateFactory)
      .copy(updated = Set(labelId1, labelId2), created = Set(labelId3))

    SMono(testee.save(labelChange1)).block()
    setClock(DATE.plusHours(1))
    SMono(testee.save(labelChange2)).block()
    setClock(DATE.plusHours(2))
    SMono(testee.save(labelChange3)).block()

    val labelChanges: LabelChanges = SMono(testee.getSinceState(ACCOUNT_ID,
      labelChange1.state, Some(Limit.of(3)))).block()
    assertSoftly(softly => {
      softly.assertThat(labelChanges.created.asJava).containsExactlyInAnyOrder(labelId3)
      softly.assertThat(labelChanges.updated.asJava).containsExactlyInAnyOrder(labelId1, labelId2)
    })
  }

  @Test
  def getChangesShouldFailWhenSinceStateNotFound(): Unit = {
    assertThatThrownBy(() => SMono(testee.getSinceState(ACCOUNT_ID, stateFactory.generate())).block())
      .isInstanceOf(classOf[ChangeNotFoundException])
  }

}

class MemoryLabelChangeRepositoryTest extends LabelChangeRepositoryContract {
  var repository: MemoryLabelChangeRepository = _
  var updatableTickingClock: UpdatableTickingClock = _

  override def testee: LabelChangeRepository = repository

  override def stateFactory: State.Factory = State.Factory.DEFAULT

  override def setClock(newTime: ZonedDateTime): Unit = updatableTickingClock.setInstant(newTime.toInstant)

  @BeforeEach
  def setup(): Unit = {
    updatableTickingClock = new UpdatableTickingClock(DATE.toInstant)
    repository = MemoryLabelChangeRepository(updatableTickingClock)
  }
}
