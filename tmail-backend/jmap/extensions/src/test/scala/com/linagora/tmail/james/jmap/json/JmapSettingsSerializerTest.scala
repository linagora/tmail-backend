package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.{JmapSettingsGet, JmapSettingsObject, JmapSettingsResponse, SettingsSetError, SettingsSetRequest, SettingsSetResponse, SettingsSetUpdateRequest, SettingsUpdateResponse}
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepositoryContract.SettingsKeyString
import com.linagora.tmail.james.jmap.settings.{JmapSettingsKey, JmapSettingsPatch, JmapSettingsUpsertRequest, JmapSettingsValue}
import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, UuidState}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsObject, JsResult, JsValue, Json}

import scala.jdk.OptionConverters._

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
        ids = Some(Set(JmapSettingsObject.SETTING_SINGLETON_ID))))
  }

  @Test
  def serializeResponseShouldSuccess(): Unit = {
    val response = JmapSettingsResponse(
      accountId = AccountId.from(Username.of("bob")).toOption.get,
      state = UuidState.fromStringUnchecked("2c9f1b12-b35a-43e6-9af2-0106fb53a943"),
      list = Seq(JmapSettingsObject(JmapSettingsObject.SETTING_SINGLETON_ID,
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

  @Test
  def givenValidResetRequestThenDeserializeSetRequestShouldSucceed(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |	"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |	"update": {
        |		"singleton": {
        |			"settings": {
        |				"tdrive.attachment.import.enabled": "true",
        |				"firebase.enabled": "true"
        |			}
        |		}
        |	}
        |}""".stripMargin)

    val deserializeSetRequestResult: JsResult[SettingsSetRequest] = JmapSettingsSerializer.deserializeSetRequest(jsInput)
    val upsertRequest: JmapSettingsUpsertRequest = JmapSettingsUpsertRequest(
      settings = Map(JmapSettingsKey.liftOrThrow("tdrive.attachment.import.enabled") -> JmapSettingsValue("true"),
        JmapSettingsKey.liftOrThrow("firebase.enabled") -> JmapSettingsValue("true")))

    assertThat(deserializeSetRequestResult.isSuccess).isTrue
    assertThat(deserializeSetRequestResult.get.accountId)
      .isEqualTo(AccountId.apply("29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"))

    val setUpdateRequest: SettingsSetUpdateRequest = deserializeSetRequestResult.get.update.get("singleton")
    assertThat(setUpdateRequest
      .getResetRequest)
      .usingRecursiveComparison()
      .isEqualTo(Some(upsertRequest))

    assertThat(setUpdateRequest.getUpdatePartialRequest.toJava).isEmpty()
  }

  @Test
  def givenValidUpdatePartialRequestWithUpsertPatchThenDeserializeSetRequestShouldSucceed(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |	"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |	"update": {
        |		"singleton": {
        |			"settings/tdrive.attachment.import.enabled": "true",
        |			"settings/firebase.enabled": "true"
        |		}
        |	}
        |}""".stripMargin)

    val deserializeSetRequestResult: JsResult[SettingsSetRequest] = JmapSettingsSerializer.deserializeSetRequest(jsInput)


    val setUpdateRequest: SettingsSetUpdateRequest = deserializeSetRequestResult.get.update.get("singleton")

    assertThat(setUpdateRequest
      .getResetRequest.toJava).isEmpty

    assertThat(setUpdateRequest.getUpdatePartialRequest.get)
      .usingRecursiveComparison()
      .isEqualTo(JmapSettingsPatch(
        toUpsert = JmapSettingsUpsertRequest(
          settings = Map("tdrive.attachment.import.enabled".asSettingKey -> JmapSettingsValue("true"),
            "firebase.enabled".asSettingKey -> JmapSettingsValue("true"))),
        toRemove = Seq()))
  }

  @Test
  def givenValidUpdatePartialRequestWithRemovePatchThenDeserializeSetRequestShouldSucceed(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |	"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |	"update": {
        |		"singleton": {
        |			"settings/tdrive.attachment.import.enabled": null
        |		}
        |	}
        |}""".stripMargin)

    val deserializeSetRequestResult: JsResult[SettingsSetRequest] = JmapSettingsSerializer.deserializeSetRequest(jsInput)

    val setUpdateRequest: SettingsSetUpdateRequest = deserializeSetRequestResult.get.update.get("singleton")

    assertThat(setUpdateRequest
      .getResetRequest.toJava).isEmpty

    assertThat(setUpdateRequest.getUpdatePartialRequest.get)
      .usingRecursiveComparison()
      .isEqualTo(JmapSettingsPatch(
        toUpsert = JmapSettingsUpsertRequest(settings = Map()),
        toRemove = Seq("tdrive.attachment.import.enabled".asSettingKey)))
  }

  @Test
  def givenValidUpdatePartialRequestWithBothRemoveAndUpsertPathThenDeserializeSetRequestShouldSucceed(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |	"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |	"update": {
        |		"singleton": {
        |     "settings/firebase.enabled": "true",
        |			"settings/tdrive.attachment.import.enabled": null
        |		}
        |	}
        |}""".stripMargin)

    val deserializeSetRequestResult: JsResult[SettingsSetRequest] = JmapSettingsSerializer.deserializeSetRequest(jsInput)

    val setUpdateRequest: SettingsSetUpdateRequest = deserializeSetRequestResult.get.update.get("singleton")

    assertThat(setUpdateRequest
      .getResetRequest.toJava).isEmpty

    assertThat(setUpdateRequest.getUpdatePartialRequest.get)
      .usingRecursiveComparison()
      .isEqualTo(JmapSettingsPatch(
        toUpsert = JmapSettingsUpsertRequest(
          settings = Map("firebase.enabled".asSettingKey -> JmapSettingsValue("true"))),
        toRemove = Seq("tdrive.attachment.import.enabled".asSettingKey)))
  }

  @Test
  def serializeSetResponseShouldSucceed(): Unit = {
    val response = SettingsSetResponse(
      accountId = AccountId.from(Username.of("bob")).toOption.get,
      oldState = UuidState.fromStringUnchecked("2c9f1b12-b35a-43e6-9af2-0106fb53a943"),
      newState = UuidState.fromStringUnchecked("2c9f1b12-b35a-43e6-9af2-0106fb53a943"),
      updated = Some(Map("singleton" -> SettingsUpdateResponse(JsObject.empty))),
      notUpdated = Some(Map("singleton1" -> SettingsSetError.invalidArgument(Some(SetErrorDescription("not singleton id"))))),
      notCreated = Some(Map("createId" -> SettingsSetError.invalidArgument(Some(SetErrorDescription("not support create"))))),
      notDestroyed = Some(Map("destroyId" -> SettingsSetError.invalidArgument(Some(SetErrorDescription("not support destroy"))))))

    assertThat(JmapSettingsSerializer.serializeSetResponse(response))
      .isEqualTo(Json.parse(
        """{
          |	"accountId": "81b637d8fcd2c6da6359e6963113a1170de795e4b725b84d1e0b4cfd9ec58ce9",
          |	"oldState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
          |	"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
          |	"updated": {
          |		"singleton": {}
          |	},
          |	"notUpdated": {
          |		"singleton1": {
          |			"type": "invalidArguments",
          |			"description": "not singleton id"
          |		}
          |	},
          |	"notCreated": {
          |		"createId": {
          |			"type": "invalidArguments",
          |			"description": "not support create"
          |		}
          |	},
          |	"notDestroyed": {
          |		"destroyId": {
          |			"type": "invalidArguments",
          |			"description": "not support destroy"
          |		}
          |	}
          |}""".stripMargin))
  }
}
