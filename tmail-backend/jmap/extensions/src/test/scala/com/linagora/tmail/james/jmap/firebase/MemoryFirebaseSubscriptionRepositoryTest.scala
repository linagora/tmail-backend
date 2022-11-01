package com.linagora.tmail.james.jmap.firebase

import org.apache.james.utils.UpdatableTickingClock
import org.junit.jupiter.api.BeforeEach

class MemoryFirebaseSubscriptionRepositoryTest extends FirebaseSubscriptionRepositoryContract {
  val updatableClock = new UpdatableTickingClock(FirebaseSubscriptionRepositoryContract.NOW)
  var memoryFirebaseSubscriptionRepository: MemoryFirebaseSubscriptionRepository = _

  @BeforeEach
  def beforeEach(): Unit = {
    memoryFirebaseSubscriptionRepository = new MemoryFirebaseSubscriptionRepository(updatableClock)
  }

  override def clock: UpdatableTickingClock = updatableClock

  override def testee: FirebaseSubscriptionRepository = memoryFirebaseSubscriptionRepository
}
