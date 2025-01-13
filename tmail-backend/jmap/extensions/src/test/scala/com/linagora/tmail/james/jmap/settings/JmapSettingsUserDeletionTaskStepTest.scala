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
