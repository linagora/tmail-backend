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

import java.util.UUID

import com.linagora.tmail.james.jmap.settings.JmapSettings.{spamCleanupEnabledSetting, trashCleanupEnabledSetting}
import org.apache.james.jmap.core.UuidState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class JmapSettingsTest {
  private def settingsWithCleanupFlags(value: String): JmapSettings =
    JmapSettings(Map(
      trashCleanupEnabledSetting -> JmapSettingsValue(value),
      spamCleanupEnabledSetting -> JmapSettingsValue(value)), UuidState(UUID.randomUUID()))

  @ParameterizedTest
  @ValueSource(strings = Array("", "yes", "1", "on"))
  def cleanupEnabledShouldFallBackToFalseOnNonBooleanValue(value: String): Unit = {
    val settings = settingsWithCleanupFlags(value)

    assertThat(settings.trashCleanupEnabled()).isFalse
    assertThat(settings.spamCleanupEnabled()).isFalse
  }

  @ParameterizedTest
  @ValueSource(strings = Array("true", "TRUE", "True"))
  def cleanupEnabledShouldBeTrueWhenTrue(value: String): Unit = {
    val settings = settingsWithCleanupFlags(value)

    assertThat(settings.trashCleanupEnabled()).isTrue
    assertThat(settings.spamCleanupEnabled()).isTrue
  }

  @Test
  def cleanupEnabledShouldBeFalseWhenFalse(): Unit = {
    val settings = settingsWithCleanupFlags("false")

    assertThat(settings.trashCleanupEnabled()).isFalse
    assertThat(settings.spamCleanupEnabled()).isFalse
  }

  @Test
  def cleanupEnabledShouldBeFalseWhenAbsent(): Unit = {
    val settings = JmapSettings(Map(), UuidState(UUID.randomUUID()))

    assertThat(settings.trashCleanupEnabled()).isFalse
    assertThat(settings.spamCleanupEnabled()).isFalse
  }
}
