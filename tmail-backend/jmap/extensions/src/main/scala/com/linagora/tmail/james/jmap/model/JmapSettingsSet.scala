package com.linagora.tmail.james.jmap.model

import com.linagora.tmail.james.jmap.model.SettingsSet.OBJECT_ID
import com.linagora.tmail.james.jmap.settings.JmapSettingsUpsertRequest
import org.apache.james.jmap.core.SetError.{SetErrorDescription, SetErrorType, invalidArgumentValue, serverFailValue}
import org.apache.james.jmap.core.{AccountId, UuidState}
import org.apache.james.jmap.method.WithAccountId
import play.api.libs.json.JsObject

object SettingsSet {
  val OBJECT_ID = "singleton"
}

case class SettingsSetRequest(accountId: AccountId,
                              update: Option[Map[String, JmapSettingsUpsertRequest]],
                              create: Option[Map[String, JsObject]] = None,
                              destroy: Option[Seq[String]] = None) extends WithAccountId {
  def validateId(): Map[String, Either[IllegalArgumentException, JmapSettingsUpsertRequest]] =
    update.getOrElse(Map())
      .map({
        case (id, patch) if id.equals(OBJECT_ID) => (id, Right(patch))
        case (id, _) => (id, Left(new IllegalArgumentException(s"id $id must be singleton")))
      })
}

case class SettingsSetResponse(accountId: AccountId,
                               oldState: UuidState,
                               newState: UuidState,
                               updated: Option[Map[String, SettingsUpdateResponse]],
                               notUpdated: Option[Map[String, SettingsSetError]],
                               notCreated: Option[Map[String, SettingsSetError]],
                               notDestroyed: Option[Map[String, SettingsSetError]])

case class SettingsUpdateResponse(value: JsObject)

object SettingsSetError {
  def invalidArgument(description: Option[SetErrorDescription]) = SettingsSetError(invalidArgumentValue, description)
  def serverFail(description: Option[SetErrorDescription]) = SettingsSetError(serverFailValue, description)
}

case class SettingsSetError(`type`: SetErrorType, description: Option[SetErrorDescription])