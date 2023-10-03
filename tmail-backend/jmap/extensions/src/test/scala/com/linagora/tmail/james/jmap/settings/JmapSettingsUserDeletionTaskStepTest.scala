package com.linagora.tmail.james.jmap.settings

import com.linagora.tmail.james.jmap.settings.JmapSettingsRepositoryContract.{ALICE, BOB, SettingsKeyString, SettingsUpsertRequestMap}
import org.assertj.core.api.Assertions.{assertThat, assertThatCode}
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.SMono

class JmapSettingsUserDeletionTaskStepTest {
  var settingsRepository: JmapSettingsRepository = _
  var testee: JmapSettingsUserDeletionTaskStep = _

  @BeforeEach
  def beforeEach(): Unit = {
    settingsRepository = new MemoryJmapSettingsRepository
    testee = new JmapSettingsUserDeletionTaskStep(settingsRepository)
  }

  @Test
  def shouldRemoveJmapSettings(): Unit = {
    SMono(settingsRepository.reset(ALICE, Map(("key1", "value1"), ("key2", "value2")).asUpsertRequest)).block()

    SMono(testee.deleteUserData(ALICE)).block()

    assertThat(SMono(settingsRepository.get(ALICE)).block()).isNull()
  }

  @Test
  def shouldNotRemoveOtherUsersJmapSettings(): Unit = {
    SMono(settingsRepository.reset(ALICE, Map(("aliceKey", "aliceValue")).asUpsertRequest)).block()
    SMono(settingsRepository.reset(BOB, Map(("bobKey", "bobValue")).asUpsertRequest)).block()

    SMono(testee.deleteUserData(ALICE)).block()

    assertThat(SMono(settingsRepository.get(BOB)).block().settings)
      .isEqualTo(Map(("bobKey".asSettingKey, JmapSettingsValue("bobValue"))))
  }

  @Test
  def shouldBeIdempotent(): Unit = {
    SMono.fromPublisher(testee.deleteUserData(ALICE)).block()

    assertThatCode(() => SMono.fromPublisher(testee.deleteUserData(ALICE)).block())
      .doesNotThrowAnyException()
  }
}
