package com.linagora.openpaas.james.jmap.model

import com.linagora.openpaas.encrypted.KeyId
import com.linagora.openpaas.james.jmap.method.KeystoreCreationParseException
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, Properties, SetError}
import org.apache.james.jmap.method.WithAccountId
import play.api.libs.json.JsObject

case class KeystoreCreationId(id: Id)

case class KeystoreSetRequest(accountId: AccountId,
                              create: Option[Map[KeystoreCreationId, JsObject]]) extends WithAccountId

object KeystoreCreationRequest {
  private val serverSetProperty = Set("id")
  private val assignableProperties = Set("key")
  private val knownProperties = assignableProperties ++ serverSetProperty

  def validateProperties(jsObject: JsObject): Either[KeystoreCreationParseException, JsObject] =
    (jsObject.keys.intersect(serverSetProperty), jsObject.keys.diff(knownProperties)) match {
      case (_, unknownProperties) if unknownProperties.nonEmpty =>
        Left(KeystoreCreationParseException(SetError.invalidArguments(
          SetErrorDescription("Some unknown properties were specified"),
          Some(toProperties(unknownProperties.toSet)))))
      case (specifiedServerSetProperties, _) if specifiedServerSetProperties.nonEmpty =>
        Left(KeystoreCreationParseException(SetError.invalidArguments(
          SetErrorDescription("Some server-set properties were specified"),
          Some(toProperties(specifiedServerSetProperties.toSet)))))
      case _ => scala.Right(jsObject)
    }

  private def toProperties(strings: Set[String]): Properties = Properties(strings
    .flatMap(string => {
      val refinedValue: Either[String, NonEmptyString] = refineV[NonEmpty](string)
      refinedValue.fold(_ => None,  Some(_))
    }))
}

case class Key(value: String)

case class KeystoreCreationRequest(key: Key)

case class KeystoreSetResponse(accountId: AccountId,
                               created: Option[Map[KeystoreCreationId, KeystoreCreationResponse]],
                               notCreated: Option[Map[KeystoreCreationId, SetError]])

case class KeystoreCreationResponse(id: KeyId)
