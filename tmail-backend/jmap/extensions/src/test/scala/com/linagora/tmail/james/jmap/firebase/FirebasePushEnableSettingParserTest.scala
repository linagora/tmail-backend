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

package com.linagora.tmail.james.jmap.firebase

import com.linagora.tmail.james.jmap.settings.JmapSettingsValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FirebasePushEnableSettingParserTest {
  @Test
  def shouldReturnEnabledByDefault(): Unit = {
    assertThat(FirebasePushEnableSettingParser.parse(None).enabled)
      .isEqualTo(true)
  }

  @ParameterizedTest
  @ValueSource(strings = Array("true", "TRUE", "TRuE"))
  def shouldReturnEnabledWhenSetToEnabled(value: String): Unit = {
    assertThat(FirebasePushEnableSettingParser.parse(Some(JmapSettingsValue(value))).enabled)
      .isEqualTo(true)
  }

  @ParameterizedTest
  @ValueSource(strings = Array("false", "FALSE", "FaLsE"))
  def shouldReturnDisabledWhenSetToDisabled(value: String): Unit = {
    assertThat(FirebasePushEnableSettingParser.parse(Some(JmapSettingsValue(value))).enabled)
      .isEqualTo(false)
  }

  @Test
  def shouldFlexibleOnWrongValueAndJustReturnEnabled(): Unit = {
    assertThat(FirebasePushEnableSettingParser.parse(Some(JmapSettingsValue("whatever"))).enabled)
      .isEqualTo(true)
  }
}
