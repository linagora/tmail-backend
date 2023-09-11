package com.linagora.tmail.james.jmap.settings

import com.linagora.tmail.james.jmap.settings.JmapSettings.JmapSettingsValue
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepositoryContract.{ALICE, BOB, SettingsKeyString, SettingsUpsertRequestMap}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.SMono

class JmapSettingsUsernameChangeTaskStepTest {
  var settingsRepository: JmapSettingsRepository = _
  var testee: JmapSettingsUsernameChangeTaskStep = _

  @BeforeEach
  def beforeEach(): Unit = {
    settingsRepository = new MemoryJmapSettingsRepository
    testee = new JmapSettingsUsernameChangeTaskStep(settingsRepository)
  }

  @Test
  def shouldMigrateSettings(): Unit = {
    SMono(settingsRepository.reset(ALICE, Map(("key1", "value1"), ("key2", "value2")).asUpsertRequest)).block()

    SMono(testee.changeUsername(oldUsername = ALICE, newUsername = BOB)).block()

    assertThat(SMono(settingsRepository.get(BOB)).block().settings)
      .isEqualTo(Map(("key1".asSettingKey,  JmapSettingsValue("value1")),
        ("key2".asSettingKey, JmapSettingsValue("value2"))))
  }

  @Test
  def shouldRemoveSettingsFromOldAccount(): Unit = {
    SMono(settingsRepository.reset(ALICE, Map(("key1", "value1"), ("key2", "value2")).asUpsertRequest)).block()

    SMono(testee.changeUsername(oldUsername = ALICE, newUsername = BOB)).block()

    assertThat(SMono(settingsRepository.get(ALICE)).block()).isNull()
  }

  @Test
  def shouldNotDeleteExistingSettingsOfNewAccount(): Unit = {
    SMono(settingsRepository.reset(ALICE, Map(("oldAccountKey1", "oldAccountValue1")).asUpsertRequest)).block()
    SMono(settingsRepository.reset(BOB, Map(("newAccountKey1", "newAccountValue1")).asUpsertRequest)).block()

    SMono(testee.changeUsername(oldUsername = ALICE, newUsername = BOB)).block()

    assertThat(SMono(settingsRepository.get(BOB)).block().settings)
      .isEqualTo(Map(("oldAccountKey1".asSettingKey, JmapSettingsValue("oldAccountValue1")),
        ("newAccountKey1".asSettingKey, JmapSettingsValue("newAccountValue1"))))
  }

  @Test
  def shouldNotOverrideExistingSettingEntriesOfNewAccount(): Unit = {
    SMono(settingsRepository.reset(ALICE, Map(("key", "oldAccountValue")).asUpsertRequest)).block()
    SMono(settingsRepository.reset(BOB, Map(("key", "newAccountValue")).asUpsertRequest)).block()

    SMono(testee.changeUsername(oldUsername = ALICE, newUsername = BOB)).block()

    assertThat(SMono(settingsRepository.get(BOB)).block().settings)
      .isEqualTo(Map(("key".asSettingKey, JmapSettingsValue("newAccountValue"))))
  }
}
