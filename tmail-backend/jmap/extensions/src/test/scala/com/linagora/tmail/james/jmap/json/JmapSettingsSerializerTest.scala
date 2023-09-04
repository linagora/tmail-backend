package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.{JmapSettingsEntry, JmapSettingsGet, JmapSettingsResponse}
import com.linagora.tmail.james.jmap.settings.JmapSettings.JmapSettingsValue
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepositoryContract.SettingsKeyString
import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.core.{AccountId, UuidState}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsResult, JsValue, Json}

class JmapSettingsSerializerTest {

  @Test
  def deserializeRequestShouldSuccess(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |        "ids": ["singleton"]
        |      }""".stripMargin)

    val deserializeResult: JsResult[JmapSettingsGet] = JmapSettingsSerializer.deserializeGetRequest(jsInput)

    assertThat(deserializeResult.isSuccess)
      .isTrue
    assertThat(deserializeResult.get)
      .usingRecursiveComparison()
      .isEqualTo(JmapSettingsGet(
        accountId = AccountId.apply("29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"),
        ids = Some(Set(JmapSettingsEntry.SETTING_SINGLETON_ID))))
  }

  @Test
  def serializeResponseShouldSuccess(): Unit = {
    val response = JmapSettingsResponse(
      accountId = AccountId.from(Username.of("bob")).toOption.get,
      state = UuidState.fromStringUnchecked("2c9f1b12-b35a-43e6-9af2-0106fb53a943"),
      list = Seq(JmapSettingsEntry(JmapSettingsEntry.SETTING_SINGLETON_ID,
        state = UuidState.fromStringUnchecked("2c9f1b12-b35a-43e6-9af2-0106fb53a943"),
        Map(("key1".asSettingKey, JmapSettingsValue("value1")), ("key2".asSettingKey, JmapSettingsValue("value2"))))),
      notFound = Seq())

    assertThat(JmapSettingsSerializer.serialize(response))
      .isEqualTo(Json.parse(
        """{
          |    "accountId": "81b637d8fcd2c6da6359e6963113a1170de795e4b725b84d1e0b4cfd9ec58ce9",
          |    "state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
          |    "list": [
          |        {
          |            "id": "singleton",
          |            "settings": {
          |                "key1": "value1",
          |                "key2": "value2"
          |            }
          |        }
          |    ],
          |    "notFound": []
          |}""".stripMargin))
  }

}
