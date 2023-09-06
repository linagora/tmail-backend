package com.linagora.tmail.james.jmap.model

import com.linagora.tmail.james.jmap.json.JmapSettingsSerializer
import com.linagora.tmail.james.jmap.method.standardErrorMessage
import com.linagora.tmail.james.jmap.model.SettingsSet.OBJECT_ID
import com.linagora.tmail.james.jmap.model.SettingsSetUpdateRequest.{SETTINGS_KEY, UPDATE_PARTIAL_KEY_PREFIX}
import com.linagora.tmail.james.jmap.settings.JmapSettings.{JmapSettingsKey, JmapSettingsValue}
import com.linagora.tmail.james.jmap.settings.{JmapSettingsPatch, JmapSettingsUpsertRequest}
import org.apache.james.jmap.core.SetError.{SetErrorDescription, SetErrorType, invalidArgumentValue, serverFailValue}
import org.apache.james.jmap.core.{AccountId, UuidState}
import org.apache.james.jmap.method.WithAccountId
import play.api.libs.json.{JsError, JsNull, JsObject, JsString, JsSuccess, JsValue}

object SettingsSet {
  val OBJECT_ID = "singleton"
}

object SettingsSetUpdateRequest {
  val SETTINGS_KEY: String = "settings"
  val UPDATE_PARTIAL_KEY_PREFIX: String = SETTINGS_KEY + "/"
}

case class SettingsSetUpdateRequest(json: JsObject) {
  def validate(): Either[SettingsSetParseException, SettingsSetUpdateRequest] =
    for {
      _ <- validateCanNotDoBothResetAndUpdatePartialPatch()
      _ <- validateUpdateKey()
      - <- validateNonEmpty()
    } yield this

  def isResetRequest: Boolean = json.value.contains(SETTINGS_KEY)
  def getResetRequest: Option[JmapSettingsUpsertRequest] =
    json.value.get(SETTINGS_KEY)
      .map(JmapSettingsSerializer.upsertRequestValueReads.reads)
      .map {
        case JsSuccess(value, _) => value
        case JsError(e) => throw SettingsSetParseException(standardErrorMessage(e))
      }

  def getUpdatePartialRequest: Option[JmapSettingsPatch] =
    json.value
      .filter(_._1.startsWith(UPDATE_PARTIAL_KEY_PREFIX))
      .map({ case (key, value) => parseUpdatePartialPatch(key, value) })
      .reduceOption((a, b) => JmapSettingsPatch.merge(a, b))

  private def validateCanNotDoBothResetAndUpdatePartialPatch(): Either[SettingsSetParseException, SettingsSetUpdateRequest] =
    if (json.keys.contains(SETTINGS_KEY)
      && json.keys.exists(updateSettingKey => updateSettingKey.startsWith(UPDATE_PARTIAL_KEY_PREFIX))) {
      Left(SettingsSetParseException("Cannot perform both a reset and a partial update simultaneously"))
    } else {
      Right(this)
    }

  private def validateUpdateKey(): Either[SettingsSetParseException, SettingsSetUpdateRequest] =
    json.keys
      .filter(updateSettingKey => !(updateSettingKey.equals(SETTINGS_KEY) || updateSettingKey.startsWith(UPDATE_PARTIAL_KEY_PREFIX))) match {
      case invalidUpdateKey if invalidUpdateKey.nonEmpty => Left(SettingsSetParseException(s"update key is not valid: ${invalidUpdateKey.mkString(",")}"))
      case _ => Right(this)
    }

  private def validateNonEmpty(): Either[SettingsSetParseException, SettingsSetUpdateRequest] =
    if (json.keys.exists(updateSettingKey => updateSettingKey.equals(SETTINGS_KEY) || updateSettingKey.startsWith(UPDATE_PARTIAL_KEY_PREFIX))) {
      Right(this)
    } else {
      Left(SettingsSetParseException("update request must not be empty"))
    }

  private def parseUpdatePartialPatch(key: String, value: JsValue): JmapSettingsPatch = {
    val jmapSettingsKey: JmapSettingsKey = JmapSettingsKey.validate(key.substring(UPDATE_PARTIAL_KEY_PREFIX.length))
      .fold(e => throw SettingsSetParseException(e.getMessage),
        settingsKey => settingsKey)
    value match {
      case JsNull => JmapSettingsPatch.toRemove(jmapSettingsKey)
      case jsString: JsString => JmapSettingsPatch.toUpsert(jmapSettingsKey, JmapSettingsValue(jsString.value))
      case _ => throw SettingsSetParseException(s"$key is not a valid partial update request")
    }
  }
}

case class SettingsSetParseException(message: String) extends IllegalArgumentException(message)

case class SettingsSetRequest(accountId: AccountId,
                              update: Option[Map[String, SettingsSetUpdateRequest]],
                              create: Option[Map[String, JsObject]] = None,
                              destroy: Option[Seq[String]] = None) extends WithAccountId {
  def validateId(): Map[String, Either[IllegalArgumentException, SettingsSetUpdateRequest]] =
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