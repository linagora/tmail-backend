package com.linagora.tmail.james.jmap.json

import java.util.UUID

import com.linagora.tmail.james.jmap.publicAsset.{ImageContentType, PublicAssetCreationId, PublicAssetCreationResponse, PublicAssetId, PublicAssetSetCreationRequest, PublicAssetSetRequest, PublicAssetSetResponse, PublicURI}
import eu.timepit.refined.auto._
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, SetError, UuidState}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsResult, JsValue, Json}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

class PublicAssetSerializerTest {

  @Test
  def deserializePublicAssetSetRequestShouldSucceed(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |  "create": {
        |    "4f29": {
        |      "blobId": "1234",
        |      "identityIds": ["12", "34"]
        |    }
        |  }
        |}""".stripMargin)

    val deserializeResult: JsResult[PublicAssetSetRequest] = PublicAssetSerializer.deserializePublicAssetSetRequest(jsInput)

    assertThat(deserializeResult.isSuccess)
      .isTrue

    val setRequest: PublicAssetSetRequest = deserializeResult.get
    assertThat(setRequest.accountId.id.value)
      .isEqualTo("29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6")
    assertThat(setRequest.create.get.asJava)
      .hasSize(1)
    assertThat(setRequest.create.get.asJava)
      .containsKey(PublicAssetCreationId("4f29"))
  }

  @Test
  def deserializeCreationRequestShouldSucceed(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |  "blobId": "1234",
        |  "identityIds": ["12", "34"]
        |}""".stripMargin)

    val deserializeResult: JsResult[PublicAssetSetCreationRequest] = PublicAssetSerializer.deserializePublicAssetSetCreationRequest(jsInput)

    assertThat(deserializeResult.isSuccess)
      .isTrue
    assertThat(deserializeResult.get.blobId.value.value)
      .isEqualTo("1234")
    assertThat(deserializeResult.get.identityIds.get.ids.map(_.id.value).asJava)
      .containsExactly("12", "34")
  }

  @Test
  def deserializeCreationRequestShouldSucceedWhenIdentityIdsAreMissing(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |  "blobId": "1234"
        |}""".stripMargin)

    val deserializeResult: JsResult[PublicAssetSetCreationRequest] = PublicAssetSerializer.deserializePublicAssetSetCreationRequest(jsInput)

    assertThat(deserializeResult.isSuccess)
      .isTrue
    assertThat(deserializeResult.get.blobId.value.value)
      .isEqualTo("1234")
    assertThat(deserializeResult.get.identityIds.toJava)
      .isEmpty()
  }

  @Test
  def deserializeCreationRequestShouldFailWhenBlobIdIsMissing(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |  "identityIds": ["12", "34"]
        |}""".stripMargin)

    val deserializeResult: JsResult[PublicAssetSetCreationRequest] = PublicAssetSerializer.deserializePublicAssetSetCreationRequest(jsInput)

    assertThat(deserializeResult.isError)
      .isTrue
  }

  @Test
  def serializePublicAssetSetResponseShouldSucceed(): Unit = {
    val response = PublicAssetSetResponse(
      accountId = AccountId("29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"),
      oldState = Some(UuidState.fromStringUnchecked("2c9f1b12-b35a-43e6-9af2-0106fb53a943")),
      newState = UuidState.fromStringUnchecked("2c9f1b12-b35a-43e6-9af2-0106fb53a944"),
      created = Some(Map(
        PublicAssetCreationId("4f29") -> PublicAssetCreationResponse(
          id = PublicAssetId(UUID.fromString("3b241101-feb9-4e23-a0c0-5b8843b4a760")),
          publicURI = PublicURI.fromString("http://localhost:8080/3b241101-feb9-4e23-a0c0-5b8843b4a760").toOption.get,
          size = 1234,
          contentType = ImageContentType.validate("image/png").toOption.get)
      )),
      notCreated = Some(Map(
        PublicAssetCreationId("4f30") -> SetError.invalidArguments(SetErrorDescription("Some unknown properties were specified"))
      ))
    )

    val json = PublicAssetSerializer.serializePublicAssetSetResponse(response)
    assertThat(json)
      .isEqualTo(Json.parse(
        """
          |{
          |  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
          |  "oldState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
          |  "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a944",
          |  "created": {
          |    "4f29": {
          |      "id": "3b241101-feb9-4e23-a0c0-5b8843b4a760",
          |      "size": 1234,
          |      "publicURI": "http://localhost:8080/3b241101-feb9-4e23-a0c0-5b8843b4a760",
          |      "contentType": "image/png"
          |    }
          |  },
          |  "notCreated": {
          |    "4f30": {
          |      "type": "invalidArguments",
          |      "description": "Some unknown properties were specified"
          |    }
          |  }
          |}
          |""".stripMargin))
  }
}