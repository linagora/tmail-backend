package com.linagora.tmail.james.jmap.json

import java.util.UUID

import com.linagora.tmail.james.jmap.json.PublicAssetSerializer.PublicAssetSetUpdateReads
import com.linagora.tmail.james.jmap.publicAsset.{ImageContentType, PublicAssetCreationId, PublicAssetCreationResponse, PublicAssetId, PublicAssetSetCreationRequest, PublicAssetSetRequest, PublicAssetSetResponse, PublicAssetUpdateResponse, PublicURI, UnparsedPublicAssetId, ValidatedPublicAssetPatchObject}
import eu.timepit.refined.auto._
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, SetError, UuidState}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsObject, JsResult, JsValue, Json}

import scala.jdk.CollectionConverters._

class PublicAssetSerializerTest {

  @Test
  def deserializePublicAssetSetRequestShouldSucceed(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |  "create": {
        |    "4f29": {
        |      "blobId": "1234",
        |      "identityIds": { "2c9f1b12-b35a-43e6-9af2-0106fb53a944": true, "2c9f1b12-b35a-43e6-9af2-0106fb53a945": true }
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
  def deserializePublicAssetSetUpdateRequestShouldSucceed(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |  "update": {
        |    "4f29": {
        |      "identityIds": { "2c9f1b12-b35a-43e6-9af2-0106fb53a944": true, "2c9f1b12-b35a-43e6-9af2-0106fb53a945": true }
        |    }
        |  }
        |}""".stripMargin)

    val deserializeResult: JsResult[PublicAssetSetRequest] = PublicAssetSerializer.deserializePublicAssetSetRequest(jsInput)

    assertThat(deserializeResult.isSuccess)
      .isTrue

    val setRequest: PublicAssetSetRequest = deserializeResult.get
    assertThat(setRequest.update.get.asJava)
      .hasSize(1)
    assertThat(setRequest.update.get.asJava)
      .containsKey(UnparsedPublicAssetId("4f29"))
  }

  @Test
  def deserializeSetDestroyRequestShouldSucceed(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |  "destroy": ["4f29"]
        |}""".stripMargin)

    val deserializeResult: JsResult[PublicAssetSetRequest] = PublicAssetSerializer.deserializePublicAssetSetRequest(jsInput)

    assertThat(deserializeResult.isSuccess)
      .isTrue

    val setRequest: PublicAssetSetRequest = deserializeResult.get
    assertThat(setRequest.destroy.get.asJava)
      .containsExactly(UnparsedPublicAssetId("4f29"))
  }

  @Test
  def deserializeCreationRequestShouldSucceed(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |  "blobId": "1234",
        |  "identityIds": { "2c9f1b12-b35a-43e6-9af2-0106fb53a944": true, "2c9f1b12-b35a-43e6-9af2-0106fb53a945": true }
        |}""".stripMargin)

    val deserializeResult: JsResult[PublicAssetSetCreationRequest] = PublicAssetSerializer.deserializePublicAssetSetCreationRequest(jsInput)

    assertThat(deserializeResult.isSuccess)
      .isTrue
    assertThat(deserializeResult.get.blobId.value.value)
      .isEqualTo("1234")

    assertThat(deserializeResult.get.identityIds.get.asJava)
      .containsExactlyInAnyOrderEntriesOf(Map(
        IdentityId(UUID.fromString("2c9f1b12-b35a-43e6-9af2-0106fb53a944")) -> true,
        IdentityId(UUID.fromString("2c9f1b12-b35a-43e6-9af2-0106fb53a945")) -> true).asJava)
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
    assertThat(deserializeResult.get.identityIds.isEmpty).isTrue
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
  def deserializeCreationRequestShouldFailWhenUnknownPropertySpecified(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |  "blobId": "1234",
        |  "unknown": "something"
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
      created = Some(Map(PublicAssetCreationId("4f29") -> PublicAssetCreationResponse(
        id = PublicAssetId(UUID.fromString("3b241101-feb9-4e23-a0c0-5b8843b4a760")),
        publicURI = PublicURI.fromString("http://localhost:8080/3b241101-feb9-4e23-a0c0-5b8843b4a760").toOption.get,
        size = 1234,
        contentType = ImageContentType.validate("image/png").toOption.get))),
      notCreated = Some(Map(PublicAssetCreationId("4f30") -> SetError.invalidArguments(SetErrorDescription("Some unknown properties were specified")))),
      updated = None,
      notUpdated = None,
      destroyed = None,
      notDestroyed = None)

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

  @Test
  def serializePublicAssetSetResponseShouldReturnCorrectUpdated(): Unit = {
    val response = PublicAssetSetResponse(
      accountId = AccountId("29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"),
      oldState = Some(UuidState.fromStringUnchecked("2c9f1b12-b35a-43e6-9af2-0106fb53a943")),
      newState = UuidState.fromStringUnchecked("2c9f1b12-b35a-43e6-9af2-0106fb53a944"),
      created = None,
      notCreated = None,
      updated = Some(Map(PublicAssetId(UUID.fromString("3b241101-feb9-4e23-a0c0-5b8843b4a760")) -> PublicAssetUpdateResponse())),
      notUpdated = Some(Map(UnparsedPublicAssetId("4f30") -> SetError.invalidArguments(SetErrorDescription("Some unknown properties were specified")))),
      destroyed = None,
      notDestroyed = None)

    val json = PublicAssetSerializer.serializePublicAssetSetResponse(response)
    assertThat(json)
      .isEqualTo(Json.parse(
        """{
          |  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
          |  "oldState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
          |  "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a944",
          |  "updated": {
          |    "3b241101-feb9-4e23-a0c0-5b8843b4a760": null
          |  },
          |  "notUpdated": {
          |    "4f30": {
          |      "type": "invalidArguments",
          |      "description": "Some unknown properties were specified"
          |    }
          |  }
          |}""".stripMargin))
  }

  @Test
  def serializePublicAssetSetResponseShouldReturnCorrectDestroyed(): Unit = {
    val response = PublicAssetSetResponse(
      accountId = AccountId("29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"),
      oldState = Some(UuidState.fromStringUnchecked("2c9f1b12-b35a-43e6-9af2-0106fb53a943")),
      newState = UuidState.fromStringUnchecked("2c9f1b12-b35a-43e6-9af2-0106fb53a944"),
      created = None,
      notCreated = None,
      destroyed = Some(Seq(PublicAssetId(UUID.fromString("3b241101-feb9-4e23-a0c0-5b8843b4a760")))),
      notDestroyed = Some(Map(UnparsedPublicAssetId("4f30") -> SetError.invalidArguments(SetErrorDescription("Some unknown properties were specified")))),
      updated = None,
      notUpdated = None)

    val json = PublicAssetSerializer.serializePublicAssetSetResponse(response)
    assertThat(json)
      .isEqualTo(Json.parse(
        """{
          |  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
          |  "oldState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
          |  "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a944",
          |  "destroyed": ["3b241101-feb9-4e23-a0c0-5b8843b4a760"],
          |  "notDestroyed": {
          |    "4f30": {
          |      "type": "invalidArguments",
          |      "description": "Some unknown properties were specified"
          |    }
          |  }
          |}""".stripMargin))
  }
}

class PublicAssetSetUpdateReads {

  @Test
  def readShouldSucceedWhenResetRequest(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |    "identityIds": {
        |        "2c9f1b12-b35a-43e6-9af2-0106fb53a944": true,
        |        "2c9f1b12-b35a-43e6-9af2-0106fb53a955": true
        |    }
        |}""".stripMargin)

    val deserializeResult: JsResult[ValidatedPublicAssetPatchObject] = PublicAssetSetUpdateReads.reads(jsInput.asInstanceOf[JsObject])

    assertThat(deserializeResult.isSuccess).isTrue

    val validatedPublicAssetPatchObject: ValidatedPublicAssetPatchObject = deserializeResult.get
    assertThat(validatedPublicAssetPatchObject.resetIdentityIds.get.map(_.id.toString).asJava)
      .containsExactlyInAnyOrder("2c9f1b12-b35a-43e6-9af2-0106fb53a944", "2c9f1b12-b35a-43e6-9af2-0106fb53a955")
    assertThat(validatedPublicAssetPatchObject.identityIdsToAdd.size).isEqualTo(0)
    assertThat(validatedPublicAssetPatchObject.identityIdsToRemove.size).isEqualTo(0)
  }

  @Test
  def readShouldFailWhenIdentityIdBooleanIsFalse(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |    "identityIds": {
        |        "2c9f1b12-b35a-43e6-9af2-0106fb53a944": false
        |    }
        |}""".stripMargin)

    val deserializeResult: JsResult[ValidatedPublicAssetPatchObject] = PublicAssetSetUpdateReads.reads(jsInput.asInstanceOf[JsObject])

    assertThat(deserializeResult.isError).isTrue
  }

  @Test
  def readShouldFailWhenIdentityIdIsNull(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |    "identityIds": {
        |        "2c9f1b12-b35a-43e6-9af2-0106fb53a944": null
        |    }
        |}""".stripMargin)

    val deserializeResult: JsResult[ValidatedPublicAssetPatchObject] = PublicAssetSetUpdateReads.reads(jsInput.asInstanceOf[JsObject])

    assertThat(deserializeResult.isError).isTrue
  }

  @Test
  def readShouldFailWhenIdentityIdIsNotBoolean(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |    "identityIds": {
        |        "2c9f1b12-b35a-43e6-9af2-0106fb53a944": "false"
        |    }
        |}""".stripMargin)

    val deserializeResult: JsResult[ValidatedPublicAssetPatchObject] = PublicAssetSetUpdateReads.reads(jsInput.asInstanceOf[JsObject])

    assertThat(deserializeResult.isError).isTrue
  }

  @Test
  def readShouldFailWhenIdentityIdInvalid(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |    "identityIds": {
        |        "2c9f1b12@Invalid": true
        |    }
        |}""".stripMargin)

    val deserializeResult: JsResult[ValidatedPublicAssetPatchObject] = PublicAssetSetUpdateReads.reads(jsInput.asInstanceOf[JsObject])

    assertThat(deserializeResult.isError).isTrue
  }

  @Test
  def readShouldSucceedWhenOnlyAddPartial(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |    "identityIds/2c9f1b12-b35a-43e6-9af2-0106fb53a944": true
        |}""".stripMargin)

    val deserializeResult: JsResult[ValidatedPublicAssetPatchObject] = PublicAssetSetUpdateReads.reads(jsInput.asInstanceOf[JsObject])
    assertThat(deserializeResult.isSuccess)
      .isTrue

    val validatedPublicAssetPatchObject: ValidatedPublicAssetPatchObject = deserializeResult.get
    assertThat(validatedPublicAssetPatchObject.resetIdentityIds.size).isEqualTo(0)
    assertThat(validatedPublicAssetPatchObject.identityIdsToRemove.size).isEqualTo(0)
    assertThat(validatedPublicAssetPatchObject.identityIdsToAdd.map(_.id.toString).asJava)
      .containsExactlyInAnyOrder("2c9f1b12-b35a-43e6-9af2-0106fb53a944")
  }

  @Test
  def readShouldSucceedWhenOnlyRemovePartial(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |    "identityIds/2c9f1b12-b35a-43e6-9af2-0106fb53a944": null
        |}""".stripMargin)

    val deserializeResult: JsResult[ValidatedPublicAssetPatchObject] = PublicAssetSetUpdateReads.reads(jsInput.asInstanceOf[JsObject])
    assertThat(deserializeResult.isSuccess).isTrue

    val validatedPublicAssetPatchObject: ValidatedPublicAssetPatchObject = deserializeResult.get
    assertThat(validatedPublicAssetPatchObject.resetIdentityIds.size).isEqualTo(0)
    assertThat(validatedPublicAssetPatchObject.identityIdsToAdd.size).isEqualTo(0)
    assertThat(validatedPublicAssetPatchObject.identityIdsToRemove.map(_.id.toString).asJava)
      .containsExactlyInAnyOrder("2c9f1b12-b35a-43e6-9af2-0106fb53a944")
  }

  @Test
  def readShouldSucceedWhenMixAddAndRemovePartial(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |    "identityIds/2c9f1b12-b35a-43e6-9af2-0106fb53a944": true,
        |    "identityIds/2c9f1b12-b35a-43e6-9af2-0106fb53a945": true,
        |    "identityIds/2c9f1b12-b35a-43e6-9af2-0106fb53a955": null,
        |    "identityIds/2c9f1b12-b35a-43e6-9af2-0106fb53a956": null
        |}""".stripMargin)

    val deserializeResult: JsResult[ValidatedPublicAssetPatchObject] = PublicAssetSetUpdateReads.reads(jsInput.asInstanceOf[JsObject])
    assertThat(deserializeResult.isSuccess).isTrue

    val validatedPublicAssetPatchObject: ValidatedPublicAssetPatchObject = deserializeResult.get
    assertThat(validatedPublicAssetPatchObject.identityIdsToRemove.map(_.id.toString).asJava)
      .containsExactlyInAnyOrder("2c9f1b12-b35a-43e6-9af2-0106fb53a955", "2c9f1b12-b35a-43e6-9af2-0106fb53a956")
    assertThat(validatedPublicAssetPatchObject.identityIdsToAdd.map(_.id.toString).asJava)
      .containsExactlyInAnyOrder("2c9f1b12-b35a-43e6-9af2-0106fb53a944", "2c9f1b12-b35a-43e6-9af2-0106fb53a945")
    assertThat(validatedPublicAssetPatchObject.resetIdentityIds.size).isEqualTo(0)
  }

  @Test
  def readShouldFailWhenPartialMapValueIsFalse(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |    "identityIds/2c9f1b12-b35a-43e6-9af2-0106fb53a944": false
        |}""".stripMargin)

    val deserializeResult: JsResult[ValidatedPublicAssetPatchObject] = PublicAssetSetUpdateReads.reads(jsInput.asInstanceOf[JsObject])
    assertThat(deserializeResult.isError).isTrue
  }

  @Test
  def readShouldFailWhenUnknownProperty(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |    "unknown": {
        |        "2c9f1b12-b35a-43e6-9af2-0106fb53a944": true
        |    }
        |}""".stripMargin)

    val deserializeResult: JsResult[ValidatedPublicAssetPatchObject] = PublicAssetSetUpdateReads.reads(jsInput.asInstanceOf[JsObject])
    assertThat(deserializeResult.isError).isTrue
  }

  @Test
  def readShouldSucceedWhenIdentityIdsIsEmpty(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |    "identityIds": {
        |    }
        |}""".stripMargin)

    val deserializeResult: JsResult[ValidatedPublicAssetPatchObject] = PublicAssetSetUpdateReads.reads(jsInput.asInstanceOf[JsObject])
    assertThat(deserializeResult.isError).isFalse

    val validatedPublicAssetPatchObject: ValidatedPublicAssetPatchObject = deserializeResult.get
    assertThat(validatedPublicAssetPatchObject.resetIdentityIds.isDefined).isTrue
    assertThat(validatedPublicAssetPatchObject.resetIdentityIds.get.size).isEqualTo(0)
  }

  @Test
  def readShouldSucceedWhenIdentityIdsIsMissing(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |}""".stripMargin)

    val deserializeResult: JsResult[ValidatedPublicAssetPatchObject] = PublicAssetSetUpdateReads.reads(jsInput.asInstanceOf[JsObject])
    assertThat(deserializeResult.isError).isFalse

    val validatedPublicAssetPatchObject: ValidatedPublicAssetPatchObject = deserializeResult.get
    assertThat(validatedPublicAssetPatchObject.resetIdentityIds.isDefined).isFalse
  }
}