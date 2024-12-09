package com.linagora.tmail.james.jmap.label

import java.time.ZonedDateTime

import com.linagora.tmail.james.jmap.label.LabelChangeRepositoryContract.DATE
import org.apache.james.jmap.api.change.State
import org.apache.james.utils.UpdatableTickingClock
import org.junit.jupiter.api.BeforeEach

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