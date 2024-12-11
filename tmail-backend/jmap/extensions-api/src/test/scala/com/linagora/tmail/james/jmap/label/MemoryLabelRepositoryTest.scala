package com.linagora.tmail.james.jmap.label

import org.junit.jupiter.api.BeforeEach

class MemoryLabelRepositoryTest extends LabelRepositoryContract {
  var memoryLabelRepository: MemoryLabelRepository = _

  @BeforeEach
  def setup(): Unit = {
    memoryLabelRepository = new MemoryLabelRepository
  }

  override def testee: LabelRepository = memoryLabelRepository
}
