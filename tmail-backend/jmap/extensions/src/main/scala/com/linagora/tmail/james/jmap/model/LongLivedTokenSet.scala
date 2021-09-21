package com.linagora.tmail.james.jmap.model

import com.linagora.tmail.james.jmap.longlivedtoken.{DeviceId, LongLivedTokenId, LongLivedTokenSecret}
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, SetError}
import org.apache.james.jmap.method.WithAccountId
import play.api.libs.json.{JsObject, JsPath, JsonValidationError}

case class LongLivedTokenCreationId(id: Id)

case class LongLivedTokenCreationRequest(deviceId: DeviceId) {
  def validate: Either[LongLivedTokenCreationRequestInvalidException, LongLivedTokenCreationRequest] =
    deviceId.validate match {
      case Right(_) => Right(this)
      case Left(err) => Left(LongLivedTokenCreationRequestInvalidException(SetError.invalidArguments(SetErrorDescription(err.getMessage))))
    }
}

case class LongLivedTokenSetRequest(accountId: AccountId,
                                    create: Map[LongLivedTokenCreationId, JsObject]) extends WithAccountId

case class TokenCreationResult(id: LongLivedTokenId,
                               token: LongLivedTokenSecret)

case class LongLivedTokenSetResponse(accountId: AccountId,
                                     created: Option[Map[LongLivedTokenCreationId, TokenCreationResult]],
                                     notCreated: Option[Map[LongLivedTokenCreationId, SetError]])

object LongLivedTokenCreationRequestInvalidException {
  def parse(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): LongLivedTokenCreationRequestInvalidException = {
    val setError: SetError = errors.head match {
      case (path, Seq()) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in LongLivedTokenSet object is not valid"))
      case (path, Seq(JsonValidationError(Seq("error.path.missing")))) => SetError.invalidArguments(SetErrorDescription(s"Missing '$path' property in LongLivedTokenSet object"))
      case (path, Seq(JsonValidationError(Seq(message)))) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in LongLivedTokenSet object is not valid: $message"))
      case (path, _) => SetError.invalidArguments(SetErrorDescription(s"Unknown error on property '$path'"))
    }
    LongLivedTokenCreationRequestInvalidException(setError)
  }
}

case class LongLivedTokenCreationRequestInvalidException(error: SetError) extends Exception

object LongLivedTokenSetResults {
  def empty: LongLivedTokenSetResults = LongLivedTokenSetResults(None, None)

  def created(longLivedTokenCreationId: LongLivedTokenCreationId, tokenCreationResult: TokenCreationResult): LongLivedTokenSetResults =
    LongLivedTokenSetResults(Some(Map(longLivedTokenCreationId -> tokenCreationResult)), None)

  def notCreated(longLivedTokenCreationId: LongLivedTokenCreationId, throwable: Throwable): LongLivedTokenSetResults = {
    val setError: SetError = throwable match {
      case invalidException: LongLivedTokenCreationRequestInvalidException => invalidException.error
      case error: Throwable => SetError.serverFail(SetErrorDescription(error.getMessage))
    }
    LongLivedTokenSetResults(None, Some(Map(longLivedTokenCreationId -> setError)))
  }

  def merge(result1: LongLivedTokenSetResults, result2: LongLivedTokenSetResults): LongLivedTokenSetResults = LongLivedTokenSetResults(
    created = (result1.created ++ result2.created).reduceOption(_ ++ _),
    notCreated = (result1.notCreated ++ result2.notCreated).reduceOption(_ ++ _))
}

case class LongLivedTokenSetResults(created: Option[Map[LongLivedTokenCreationId, TokenCreationResult]],
                                    notCreated: Option[Map[LongLivedTokenCreationId, SetError]]) {
  def asResponse(accountId: AccountId): LongLivedTokenSetResponse = LongLivedTokenSetResponse(accountId, created, notCreated)
}

