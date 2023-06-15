package com.linagora.tmail.james.jmap

import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.json.ResponseSerializer
import play.api.libs.json.{JsError, JsPath, JsResult, JsonValidationError}

import scala.language.implicitConversions

package object method {

  def standardErrorMessage(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): String =
    errors.head match {
      case (path, Seq()) => s"'$path' property is not valid"
      case (path, Seq(JsonValidationError(Seq("error.path.missing")))) => s"Missing '$path' property"
      case (path, Seq(JsonValidationError(Seq("error.expected.jsarray")))) => s"'$path' property need to be an array"
      case (path, Seq(JsonValidationError(Seq(message)))) => s"'$path' property is not valid: $message"
      case (path, _) => s"Unknown error on property '$path'"
      case _ => ResponseSerializer.serialize(JsError(errors)).toString()
    }

  def standardError(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): SetError =
    SetError.invalidArguments(SetErrorDescription(standardErrorMessage(errors)))

  implicit class AsEitherRequest[T](val jsResult: JsResult[T]) {
    def asEitherRequest: Either[IllegalArgumentException, T] =
      jsResult.asEither.left.map(errors => new IllegalArgumentException(standardErrorMessage(errors)))
  }
}