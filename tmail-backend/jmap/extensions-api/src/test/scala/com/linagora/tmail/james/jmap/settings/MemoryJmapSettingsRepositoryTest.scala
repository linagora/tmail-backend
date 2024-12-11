package com.linagora.tmail.james.jmap.settings

import org.junit.jupiter.api.BeforeEach

class MemoryJmapSettingsRepositoryTest extends JmapSettingsRepositoryContract {
  var memoryJmapSettingsRepository: MemoryJmapSettingsRepository = _

  @BeforeEach
  def setup(): Unit = {
    memoryJmapSettingsRepository = new MemoryJmapSettingsRepository
  }

  override def testee: MemoryJmapSettingsRepository = memoryJmapSettingsRepository
}
