/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.jmap.settings

import java.time.Duration
import java.util.Map.entry

import com.linagora.tmail.james.jmap.settings.JmapSettingsRepositoryContract.{ALICE, BOB, SAMPLE_UPSERT_REQUEST, SettingsKeyString, SettingsUpsertRequestMap, SettingsUpsertRequestPair}
import org.apache.james.core.Username
import org.apache.james.jmap.core.UuidState
import org.apache.james.util.concurrency.ConcurrentTestRunner
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
    SMono(testee.reset(BOB, SAMPLE_UPSERT_REQUEST)).block()
    val jmapSettings: JmapSettings = SMono(testee.get(BOB)).block()
    assertThat(jmapSettings.settings.asJava)
      .containsExactly(entry("key1".asSettingKey, JmapSettingsValue("value1")))
  }

  @Test
  def getShouldNotReturnEntryOfOtherUser(): Unit = {
    SMono(testee.reset(BOB, SAMPLE_UPSERT_REQUEST)).block()
    assertThat(SMono(testee.get(ALICE)).block()).isNull()
  }

  @Test
  def getShouldReturnLatestState(): Unit = {
    val state1 = SMono(testee.reset(BOB, ("key1", "value1").asUpsertRequest)).block()
    val state2 = SMono(testee.reset(BOB, ("key1", "value2").asUpsertRequest)).block()
    val jmapSettings: JmapSettings = SMono(testee.get(BOB)).block()
    assertThat(jmapSettings.state).isNotEqualTo(JmapSettingsStateFactory.INITIAL)
    assertThat(jmapSettings.state).isEqualTo(state2.newState)
  }

  @Test
  def getLatestStateShouldReturnInitialStateByDefault(): Unit = {
    assertThat(SMono(testee.getLatestState(BOB)).block())
      .isEqualTo(JmapSettingsStateFactory.INITIAL)
  }

  @Test
  def getLatestStateShouldReturnOnlyLatestState(): Unit = {
    val state1 = SMono(testee.reset(BOB, ("key1", "value1").asUpsertRequest)).block()
    val state2 = SMono(testee.reset(BOB, ("key1", "value2").asUpsertRequest)).block()

    val latestState: UuidState = SMono(testee.getLatestState(BOB)).block()

    assertThat(latestState).isEqualTo(state2.newState)
  }

  @Test
  def resetShouldNotThrowEvenWhenNoPreviousSettings(): Unit = {
    assertThatCode(() => SMono(testee.reset(BOB, SAMPLE_UPSERT_REQUEST)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def resetShouldRemoveRedundantSettingEntry(): Unit = {
    SMono(testee.reset(BOB, ("key1", "value1").asUpsertRequest)).block()
    SMono(testee.reset(BOB, ("key2", "value2").asUpsertRequest)).block()

    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactly(entry("key2".asSettingKey, JmapSettingsValue("value2")))
  }

  @Test
  def resetShouldOverrideExistSettingEntry(): Unit = {
    SMono(testee.reset(BOB, ("key1", "value1").asUpsertRequest)).block()
    SMono(testee.reset(BOB, ("key1", "value2").asUpsertRequest)).block()

    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactly(entry("key1".asSettingKey, JmapSettingsValue("value2")))
  }

  @Test
  def resetShouldUpdateState(): Unit = {
    val state1 = SMono(testee.reset(BOB, ("key1", "value1").asUpsertRequest)).block()
    val state2 = SMono(testee.reset(BOB, ("key2", "value2").asUpsertRequest)).block()
    assertThat(state2.oldState).isEqualTo(state1.newState)
    assertThat(state2.newState).isNotEqualTo(state2.oldState)
  }

  @Test
  def resetShouldNotAffectToOtherUser(): Unit = {
    SMono(testee.reset(BOB, ("key1", "value1").asUpsertRequest)).block()
    SMono(testee.reset(ALICE, ("key1", "value2").asUpsertRequest)).block()

    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactly(entry("key1".asSettingKey, JmapSettingsValue("value1")))
  }

  @Test
  def updatePartialShouldRemoveSettingEntryInPatch(): Unit = {
    SMono(testee.reset(BOB, Map(("key1", "value1"), ("key2", "value2")).asUpsertRequest)).block()
    SMono(testee.updatePartial(BOB, JmapSettingsPatch(JmapSettingsUpsertRequest(Map()),
      Seq("key1".asSettingKey)))).block()

    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactly(entry("key2".asSettingKey, JmapSettingsValue("value2")))
  }

  @Test
  def updatePartialShouldNotThrowWhenRemoveKeyDoesNotExist(): Unit = {
    SMono(testee.reset(BOB, Map(("key1", "value1"), ("key2", "value2")).asUpsertRequest)).block()
    assertThatCode(() => SMono(testee.updatePartial(BOB, JmapSettingsPatch(JmapSettingsUpsertRequest(Map()),
      Seq("key3".asSettingKey)))).block())
      .doesNotThrowAnyException()
  }

  @Test
  def updatePartialShouldUpdateState(): Unit = {
    val state1 = SMono(testee.reset(BOB, Map(("key1", "value1"), ("key2", "value2")).asUpsertRequest)).block()
    val state2 = SMono(testee.updatePartial(BOB, JmapSettingsPatch(JmapSettingsUpsertRequest(Map()),
      Seq("key1".asSettingKey)))).block()

    assertThat(state2.oldState).isEqualTo(state1.newState)
    assertThat(state2.newState).isNotEqualTo(state2.oldState)
  }

  @Test
  def updatePartialShouldInsertSettingsWhenUserHasNoSettings(): Unit = {
    val settingsStateUpdate: SettingsStateUpdate = SMono(testee.updatePartial(BOB, JmapSettingsPatch(
      JmapSettingsUpsertRequest(Map("key3".asSettingKey -> JmapSettingsValue("value3"))),
      Seq()))).block()

    assertThat(settingsStateUpdate.oldState).isEqualTo(JmapSettingsStateFactory.INITIAL)
    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactlyInAnyOrderEntriesOf(java.util.Map.ofEntries(entry("key3".asSettingKey, JmapSettingsValue("value3"))))
  }

  @Test
  def updatePartialShouldAppendNewSettingEntryInPatch(): Unit = {
    SMono(testee.reset(BOB, Map(("key1", "value1")).asUpsertRequest)).block()
    SMono(testee.updatePartial(BOB, JmapSettingsPatch(
      JmapSettingsUpsertRequest(Map("key3".asSettingKey -> JmapSettingsValue("value3"))),
      Seq()))).block()

    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactlyInAnyOrderEntriesOf(java.util.Map.ofEntries(entry("key1".asSettingKey, JmapSettingsValue("value1")),
        entry("key3".asSettingKey, JmapSettingsValue("value3"))))
  }

  @Test
  def updatePartialShouldSucceedWhenMixCases(): Unit = {
    SMono(testee.reset(BOB, Map(("key1", "value1"), ("key2", "value2")).asUpsertRequest)).block()
    SMono(testee.updatePartial(BOB, JmapSettingsPatch(
      JmapSettingsUpsertRequest(Map("key3".asSettingKey -> JmapSettingsValue("value3"), "key1".asSettingKey -> JmapSettingsValue("value4"))),
      Seq("key2".asSettingKey)))).block()

    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactlyInAnyOrderEntriesOf(java.util.Map.ofEntries(entry("key1".asSettingKey, JmapSettingsValue("value4")),
        entry("key3".asSettingKey, JmapSettingsValue("value3"))))
  }

  @Test
  def updatePartialShouldFailWhenBothUpsertAndRemovePatchContainSameKey(): Unit = {
    SMono(testee.reset(BOB, Map(("key1", "value1"), ("key2", "value2")).asUpsertRequest)).block()
    assertThatThrownBy(() => SMono(testee.updatePartial(BOB, JmapSettingsPatch(
      JmapSettingsUpsertRequest(Map("key1".asSettingKey -> JmapSettingsValue("value3"))),
      Seq("key1".asSettingKey)))).block())
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def updatePartialShouldFailWhenBothUpsertAndRemovePatchIsEmpty(): Unit = {
    SMono(testee.reset(BOB, Map(("key1", "value1"), ("key2", "value2")).asUpsertRequest)).block()
    assertThatThrownBy(() => SMono(testee.updatePartial(BOB, JmapSettingsPatch(
      JmapSettingsUpsertRequest(Map()),
      Seq()))).block())
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def updatePartialShouldNotAffectToOtherUser(): Unit = {
    SMono(testee.reset(BOB, Map(("key1", "value1")).asUpsertRequest)).block()
    SMono(testee.reset(ALICE, Map(("key1", "value1")).asUpsertRequest)).block()
    SMono(testee.updatePartial(ALICE, JmapSettingsPatch(
      JmapSettingsUpsertRequest(Map()),
      Seq("key1".asSettingKey)))).block()

    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactly(entry("key1".asSettingKey, JmapSettingsValue("value1")))
  }

  @Test
  def deleteShouldRemoveUserSettings(): Unit = {
    SMono(testee.reset(BOB, Map(("key1", "value1"), ("key2", "value2")).asUpsertRequest)).block()

    SMono(testee.delete(BOB)).block()

    assertThat(SMono(testee.get(BOB)).block()).isNull()
  }

  @Test
  def deleteShouldBeIdempotent(): Unit = {
    SMono(testee.reset(BOB, Map(("key1", "value1"), ("key2", "value2")).asUpsertRequest)).block()

    SMono(testee.delete(BOB)).block()

    assertThatCode(() => SMono(testee.delete(BOB)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def deleteShouldNotRemoveOtherUserSettings(): Unit = {
    SMono(testee.reset(BOB, Map(("key1", "value1")).asUpsertRequest)).block()

    SMono(testee.delete(ALICE)).block()

    assertThat(SMono(testee.get(BOB)).block().settings.asJava)
      .containsExactly(entry("key1".asSettingKey, JmapSettingsValue("value1")))
  }

  @Test
  def concurrentUpdatePartialTest(): Unit = {
    SMono(testee.reset(BOB, Map(("key1", "value1")).asUpsertRequest)).block()
    ConcurrentTestRunner.builder()
      .operation((threadNumber, step) => SMono(testee.updatePartial(BOB,
        JmapSettingsPatch(JmapSettingsUpsertRequest(Map(s"key$threadNumber-$step".asSettingKey -> JmapSettingsValue(s"value$threadNumber-$step"))), Seq()))).block())
      .threadCount(10)
      .operationCount(10)
      .runSuccessfullyWithin(Duration.ofSeconds(5))

    assertThat(SMono(testee.get(BOB)).flatMap(item => SMono.just(item.settings.keys.size)).block()).isEqualTo(101)
  }
}
