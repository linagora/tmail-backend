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
