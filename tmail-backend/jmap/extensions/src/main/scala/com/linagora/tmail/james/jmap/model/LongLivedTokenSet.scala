package com.linagora.tmail.james.jmap.model

import com.linagora.tmail.james.jmap.longlivedtoken.{AuthenticationToken, DeviceId, LongLivedTokenId, UnparsedLongLivedTokenId}
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
                                    create: Option[Map[LongLivedTokenCreationId, JsObject]],
                                    destroy: Option[List[UnparsedLongLivedTokenId]]) extends WithAccountId

case class TokenCreationResult(id: LongLivedTokenId,
                               token: AuthenticationToken)

case class LongLivedTokenSetResponse(accountId: AccountId,
                                     created: Option[Map[LongLivedTokenCreationId, TokenCreationResult]],
                                     destroyed: Option[Seq[LongLivedTokenId]],
                                     notCreated: Option[Map[LongLivedTokenCreationId, SetError]],
                                     notDestroyed: Option[Map[UnparsedLongLivedTokenId, SetError]])

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

object LongLivedTokenCreationResults {
  def empty: LongLivedTokenCreationResults = LongLivedTokenCreationResults(None, None)

  def created(longLivedTokenCreationId: LongLivedTokenCreationId, tokenCreationResult: TokenCreationResult): LongLivedTokenCreationResults =
    LongLivedTokenCreationResults(Some(Map(longLivedTokenCreationId -> tokenCreationResult)), None)

  def notCreated(longLivedTokenCreationId: LongLivedTokenCreationId, throwable: Throwable): LongLivedTokenCreationResults = {
    val setError: SetError = throwable match {
      case invalidException: LongLivedTokenCreationRequestInvalidException => invalidException.error
      case error: Throwable => SetError.serverFail(SetErrorDescription(error.getMessage))
    }
    LongLivedTokenCreationResults(None, Some(Map(longLivedTokenCreationId -> setError)))
  }

  def merge(result1: LongLivedTokenCreationResults, result2: LongLivedTokenCreationResults): LongLivedTokenCreationResults = LongLivedTokenCreationResults(
    created = (result1.created ++ result2.created).reduceOption(_ ++ _),
    notCreated = (result1.notCreated ++ result2.notCreated).reduceOption(_ ++ _))
}

case class LongLivedTokenCreationResults(created: Option[Map[LongLivedTokenCreationId, TokenCreationResult]],
                                         notCreated: Option[Map[LongLivedTokenCreationId, SetError]])

sealed trait LongLivedTokenDestroyResult
case class LongLivedTokenDestroySuccess(id: LongLivedTokenId) extends LongLivedTokenDestroyResult
case class LongLivedTokenDestroyFailure(id: UnparsedLongLivedTokenId, throwable: Throwable) extends LongLivedTokenDestroyResult {
  def asLongLivedTokenSetError: SetError = throwable match {
    case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(s"${id.value} is not a LongLivedTokenId: ${e.getMessage}"))
    case _ => SetError.serverFail(SetErrorDescription(throwable.getMessage))
  }
}

case class LongLivedTokenDestroyResults(values: Seq[LongLivedTokenDestroyResult]) {
  def retrieveDestroyed: Seq[LongLivedTokenId] = values.flatMap {
    case success: LongLivedTokenDestroySuccess => Some(success)
    case _ => None
  }.map(_.id)

  def retrieveNotDestroyed: Map[UnparsedLongLivedTokenId, SetError] = values.flatMap {
    case failure: LongLivedTokenDestroyFailure => Some(failure.id, failure.asLongLivedTokenSetError)
    case _ => None
  }.toMap
}