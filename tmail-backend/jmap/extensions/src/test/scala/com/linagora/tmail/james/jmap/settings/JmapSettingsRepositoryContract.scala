package com.linagora.tmail.james.jmap.settings

import java.util.Map.entry

import com.linagora.tmail.james.jmap.settings.JmapSettings.{JmapSettingsKey, JmapSettingsValue}
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepositoryContract.{ALICE, BOB, SAMPLE_UPSERT_REQUEST, SettingsKeyString, SettingsUpsertRequestMap, SettingsUpsertRequestPair}
import org.apache.james.core.Username
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._

object JmapSettingsRepositoryContract {
  val ALICE: Username = Username.of("alice")
  val BOB: Username = Username.of("bob")
  val SAMPLE_UPSERT_REQUEST: JmapSettingsUpsertRequest = ("key1", "value1").asUpsertRequest

  implicit class SettingsKeyString(val string: String) {
    def asSettingKey: JmapSettingsKey = JmapSettingsKey.liftOrThrow(string)
  }

  implicit class SettingsUpsertRequestPair(val setting: (String, String)) {
    def asUpsertRequest: JmapSettingsUpsertRequest = JmapSettingsUpsertRequest(Map(setting).map(kv => kv._1.asSettingKey -> JmapSettingsValue(kv._2)))
  }

  implicit class SettingsUpsertRequestMap(val setting: Map[String, String]) {
    def asUpsertRequest: JmapSettingsUpsertRequest = JmapSettingsUpsertRequest(setting.map(kv => kv._1.asSettingKey -> JmapSettingsValue(kv._2)))
  }
}

trait JmapSettingsRepositoryContract {

  def testee: JmapSettingsRepository

  @Test
  def getShouldReturnEmptyByDefault(): Unit = {
    assertThat(SMono(testee.get(BOB)).block()).isNull()
  }

  @Test
  def getShouldReturnSavedSettings(): Unit = {
    SMono(testee.save(BOB, SAMPLE_UPSERT_REQUEST)).block()
    val jmapSettings: JmapSettings = SMono(testee.get(BOB)).block()
    assertThat(jmapSettings.settings.asJava)
      .containsExactly(entry("key1".asSettingKey, JmapSettingsValue("value1")))
  }

  @Test
  def getShouldNotReturnEntryOfOtherUser(): Unit = {
    SMono(testee.save(BOB, SAMPLE_UPSERT_REQUEST)).block()
    assertThat(SMono(testee.get(ALICE)).block()).isNull()
  }

  @Test
  def getShouldReturnLatestState(): Unit = {
    val state1 = SMono(testee.save(BOB, ("key1", "value1").asUpsertRequest)).block()
    val state2 = SMono(testee.save(BOB, ("key1", "value2").asUpsertRequest)).block()
    val jmapSettings: JmapSettings = SMono(testee.get(BOB)).block()
    assertThat(jmapSettings.state).isNotEqualTo(JmapSettingsStateFactory.INITIAL)
    assertThat(jmapSettings.state).isEqualTo(state2.newState)
  }

  @Test
  def saveShouldSucceedWhenValidRequest(): Unit = {
    assertThatCode(() => SMono(testee.save(BOB, SAMPLE_UPSERT_REQUEST)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def saveShouldStoreEntry(): Unit = {
    SMono(testee.save(BOB, SAMPLE_UPSERT_REQUEST)).block()
    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .hasSize(1)
  }

  @Test
  def saveShouldReturnUpdateState(): Unit = {
    val state: SettingsStateUpdate = SMono(testee.save(BOB, SAMPLE_UPSERT_REQUEST)).block()
    assertThat(state.oldState).isEqualTo(JmapSettingsStateFactory.INITIAL)
    assertThat(state.newState).isNotEqualTo(JmapSettingsStateFactory.INITIAL)
  }

  @Test
  def saveShouldUpdateState(): Unit = {
    val state1 = SMono(testee.save(BOB, ("key1", "value1").asUpsertRequest)).block()
    val state2 = SMono(testee.save(BOB, ("key1", "value2").asUpsertRequest)).block()
    assertThat(state2.oldState).isEqualTo(state1.newState)
    assertThat(state2.newState).isNotEqualTo(state2.oldState)
  }

  @Test
  def saveShouldAppendNewSettings(): Unit = {
    SMono(testee.save(BOB, ("key1", "value1").asUpsertRequest)).block()
    SMono(testee.save(BOB, ("key2", "value2").asUpsertRequest)).block()

    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactly(entry("key1".asSettingKey, JmapSettingsValue("value1")),
        entry("key2".asSettingKey, JmapSettingsValue("value2")))
  }

  @Test
  def saveShouldOverrideOldSettings(): Unit = {
    SMono(testee.save(BOB, ("key1", "value1").asUpsertRequest)).block()
    SMono(testee.save(BOB, ("key1", "value2").asUpsertRequest)).block()
    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactly(entry("key1".asSettingKey, JmapSettingsValue("value2")))
  }

  @Test
  def saveShouldOverrideSettingEntryOfOtherUser(): Unit = {
    SMono(testee.save(BOB, ("key1", "value1").asUpsertRequest)).block()
    SMono(testee.save(ALICE, ("key1", "value2").asUpsertRequest)).block()
    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactly(entry("key1".asSettingKey, JmapSettingsValue("value1")))
  }

  @Test
  def resetShouldNotThrowEvenWhenNoPreviousSettings(): Unit = {
    assertThatCode(() => SMono(testee.reset(BOB, SAMPLE_UPSERT_REQUEST)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def resetShouldRemoveRedundantSettingEntry(): Unit = {
    SMono(testee.save(BOB, ("key1", "value1").asUpsertRequest)).block()
    SMono(testee.reset(BOB, ("key2", "value2").asUpsertRequest)).block()

    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactly(entry("key2".asSettingKey, JmapSettingsValue("value2")))
  }

  @Test
  def resetShouldOverrideExistSettingEntry(): Unit = {
    SMono(testee.save(BOB, ("key1", "value1").asUpsertRequest)).block()
    SMono(testee.reset(BOB, ("key1", "value2").asUpsertRequest)).block()

    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactly(entry("key1".asSettingKey, JmapSettingsValue("value2")))
  }

  @Test
  def resetShouldUpdateState(): Unit = {
    val state1 = SMono(testee.save(BOB, ("key1", "value1").asUpsertRequest)).block()
    val state2 = SMono(testee.reset(BOB, ("key2", "value2").asUpsertRequest)).block()
    assertThat(state2.oldState).isEqualTo(state1.newState)
    assertThat(state2.newState).isNotEqualTo(state2.oldState)
  }

  @Test
  def resetShouldNotAffectToOtherUser(): Unit = {
    SMono(testee.save(BOB, ("key1", "value1").asUpsertRequest)).block()
    SMono(testee.reset(ALICE, ("key1", "value2").asUpsertRequest)).block()

    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactly(entry("key1".asSettingKey, JmapSettingsValue("value1")))
  }

  @Test
  def updatePartialShouldRemoveSettingEntryInPatch(): Unit = {
    SMono(testee.save(BOB, Map(("key1", "value1"), ("key2", "value2")).asUpsertRequest)).block()
    SMono(testee.updatePartial(BOB, JmapSettingsPatch(JmapSettingsUpsertRequest(Map()),
      Seq("key1".asSettingKey)))).block()

    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactly(entry("key2".asSettingKey, JmapSettingsValue("value2")))
  }

  @Test
  def updatePartialShouldNotThrowWhenRemoveKeyDoesNotExist(): Unit = {
    SMono(testee.save(BOB, Map(("key1", "value1"), ("key2", "value2")).asUpsertRequest)).block()
    assertThatCode(() => SMono(testee.updatePartial(BOB, JmapSettingsPatch(JmapSettingsUpsertRequest(Map()),
      Seq("key3".asSettingKey)))).block())
      .doesNotThrowAnyException()
  }

  @Test
  def updatePartialShouldUpdateState(): Unit = {
    val state1 = SMono(testee.save(BOB, Map(("key1", "value1"), ("key2", "value2")).asUpsertRequest)).block()
    val state2 = SMono(testee.updatePartial(BOB, JmapSettingsPatch(JmapSettingsUpsertRequest(Map()),
      Seq("key1".asSettingKey)))).block()

    assertThat(state2.oldState).isEqualTo(state1.newState)
    assertThat(state2.newState).isNotEqualTo(state2.oldState)
  }

  @Test
  def updatePartialShouldAppendNewSettingEntryInPatch(): Unit = {
    SMono(testee.save(BOB, Map(("key1", "value1")).asUpsertRequest)).block()
    SMono(testee.updatePartial(BOB, JmapSettingsPatch(
      JmapSettingsUpsertRequest(Map("key3".asSettingKey -> JmapSettingsValue("value3"))),
      Seq()))).block()

    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactly(entry("key1".asSettingKey, JmapSettingsValue("value1")),
        entry("key3".asSettingKey, JmapSettingsValue("value3")))
  }

  @Test
  def updatePartialShouldSucceedWhenMixCases(): Unit = {
    SMono(testee.save(BOB, Map(("key1", "value1"), ("key2", "value2")).asUpsertRequest)).block()
    SMono(testee.updatePartial(BOB, JmapSettingsPatch(
      JmapSettingsUpsertRequest(Map("key3".asSettingKey -> JmapSettingsValue("value3"), "key1".asSettingKey -> JmapSettingsValue("value4"))),
      Seq("key2".asSettingKey)))).block()

    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactly(entry("key1".asSettingKey, JmapSettingsValue("value4")),
        entry("key3".asSettingKey, JmapSettingsValue("value3")))
  }

  @Test
  def updatePartialShouldFailWhenBothUpsertAndRemovePatchContainSameKey(): Unit = {
    SMono(testee.save(BOB, Map(("key1", "value1"), ("key2", "value2")).asUpsertRequest)).block()
    assertThatThrownBy(() => SMono(testee.updatePartial(BOB, JmapSettingsPatch(
      JmapSettingsUpsertRequest(Map("key1".asSettingKey -> JmapSettingsValue("value3"))),
      Seq("key1".asSettingKey)))).block())
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def updatePartialShouldFailWhenBothUpsertAndRemovePatchIsEmpty(): Unit = {
    SMono(testee.save(BOB, Map(("key1", "value1"), ("key2", "value2")).asUpsertRequest)).block()
    assertThatThrownBy(() => SMono(testee.updatePartial(BOB, JmapSettingsPatch(
      JmapSettingsUpsertRequest(Map()),
      Seq()))).block())
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def updatePartialShouldNotAffectToOtherUser(): Unit = {
    SMono(testee.save(BOB, Map(("key1", "value1")).asUpsertRequest)).block()
    SMono(testee.save(ALICE, Map(("key1", "value1")).asUpsertRequest)).block()
    SMono(testee.updatePartial(ALICE, JmapSettingsPatch(
      JmapSettingsUpsertRequest(Map()),
      Seq("key1".asSettingKey)))).block()

    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactly(entry("key1".asSettingKey, JmapSettingsValue("value1")))
  }

}
