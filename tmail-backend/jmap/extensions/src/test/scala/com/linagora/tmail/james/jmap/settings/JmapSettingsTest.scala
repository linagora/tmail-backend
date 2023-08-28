package com.linagora.tmail.james.jmap.settings

import com.linagora.tmail.james.jmap.settings.JmapSettings.JmapSettingsKey
import org.assertj.core.api.Assertions.{assertThatCode, assertThatThrownBy}
import org.junit.jupiter.api.Test

class JmapSettingsTest {

  @Test
  def settingsKeyNotAcceptForwardSlash(): Unit = {
    assertThatThrownBy(() => JmapSettingsKey.liftOrThrow("a/b"))
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def theValidateShouldSucceedWhenKeyIsValid(): Unit = {
    assertThatCode(() => JmapSettingsKey.liftOrThrow("tdrive.attachment.import.enabled"))
      .doesNotThrowAnyException()
  }

}
