package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.{HasMoreChanges, LabelChangesRequest, LabelChangesResponse, LabelId}
import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.api.change.Limit
import org.apache.james.jmap.core.{AccountId, UuidState}
import org.apache.james.jmap.mail.Keyword
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsResult, JsValue, Json}

class LabelChangesSerializerTest {

  @Test
  def deserializeRequestShouldSuccess(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |        "sinceState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
        |        "maxChanges": 10
        |      }""".stripMargin)

    val deserializeResult: JsResult[LabelChangesRequest] = LabelChangesSerializer.deserializeRequest(jsInput)

    assertThat(deserializeResult.isSuccess)
      .isTrue
    assertThat(deserializeResult.get)
      .usingRecursiveComparison()
      .isEqualTo(LabelChangesRequest(
        accountId = AccountId.apply("29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"),
        sinceState = UuidState.fromStringUnchecked("2c9f1b12-b35a-43e6-9af2-0106fb53a943"),
        maxChanges = Some(Limit.of(10))))
  }

  @Test
  def serializeResponseShouldSuccess(): Unit = {
    val response = LabelChangesResponse(
      accountId = AccountId.from(Username.of("bob")).toOption.get,
      oldState = UuidState.fromStringUnchecked("2c9f1b12-b35a-43e6-9af2-0106fb53a943"),
      newState = UuidState.fromStringUnchecked("3c9f1b12-b35a-43e6-9af2-0106fb53a943"),
      hasMoreChanges = HasMoreChanges(true),
      created = Set(LabelId.fromKeyword(Keyword.parse("label1").toOption.get)),
      updated = Set(LabelId.fromKeyword(Keyword.parse("label2").toOption.get)),
      destroyed = Set(LabelId.fromKeyword(Keyword.parse("label3").toOption.get)))

    assertThat(LabelChangesSerializer.serialize(response))
      .isEqualTo(Json.parse(
        """{
          |	"accountId": "81b637d8fcd2c6da6359e6963113a1170de795e4b725b84d1e0b4cfd9ec58ce9",
          |	"oldState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
          |	"newState": "3c9f1b12-b35a-43e6-9af2-0106fb53a943",
          |	"hasMoreChanges": true,
          |	"created": [
          |		"label1"
          |	],
          |	"updated": [
          |		"label2"
          |	],
          |	"destroyed": [
          |		"label3"
          |	]
          |}""".stripMargin))
  }

}
