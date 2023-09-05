package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.JmapSettingsEntry.JmapSettingsId
import com.linagora.tmail.james.jmap.model.{JmapSettingsEntry, JmapSettingsGet, JmapSettingsResponse, SettingsSetError, SettingsSetRequest, SettingsSetResponse, SettingsUpdateResponse}
import com.linagora.tmail.james.jmap.settings.JmapSettings.{JmapSettingsKey, JmapSettingsValue}
import com.linagora.tmail.james.jmap.settings.JmapSettingsUpsertRequest
import org.apache.james.jmap.core.UuidState
import org.apache.james.jmap.json.mapWrites
import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{JsError, JsPath, JsResult, JsString, JsSuccess, JsValue, Json, Reads, Writes}

object JmapSettingsSerializer {

  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]

  private implicit val jmapSettingsValueWrites: Writes[JmapSettingsValue] = json => Json.toJson(json.value)
  private implicit val mapSettings: Writes[Map[JmapSettingsKey, JmapSettingsValue]] =
    mapWrites[JmapSettingsKey, JmapSettingsValue](_.asString(), jmapSettingsValueWrites)

  private implicit val jmapSettingsEntryWrites: Writes[JmapSettingsEntry] =
    ((JsPath \ "id").write[JmapSettingsId] and
      (JsPath \ "settings").write[Map[JmapSettingsKey, JmapSettingsValue]]
      )(unlift(JmapSettingsEntry.unapplyIgnoreState))


  private implicit val jmapSettingsResponseWrites: Writes[JmapSettingsResponse] = Json.writes[JmapSettingsResponse]
  private implicit val jmapSettingsGetReads: Reads[JmapSettingsGet] = Json.reads[JmapSettingsGet]

  private implicit val jmapSettingsKeyReads: Reads[JmapSettingsKey] = {
    case jsString: JsString => JmapSettingsKey.validate(jsString.value)
      .fold(e => JsError(e.getMessage),
        settingsKey => JsSuccess(settingsKey))
    case _ => JsError("Expecting a string as a Settings key")
  }
  private implicit val jmapSettingsValueReads: Reads[JmapSettingsValue] = Json.valueReads[JmapSettingsValue]
  private implicit val settingsMapUpdateRequestReads: Reads[Map[JmapSettingsKey, JmapSettingsValue]] =
    Reads.mapReads[JmapSettingsKey, JmapSettingsValue] {keyString => jmapSettingsKeyReads.reads(JsString(keyString))}
  private implicit val jmapSettingsUpsertRequestReads: Reads[JmapSettingsUpsertRequest] = Json.reads[JmapSettingsUpsertRequest]
  private implicit val settingsSetRequestReads: Reads[SettingsSetRequest] = Json.reads[SettingsSetRequest]

  private implicit val settingsSetUpdateResponseWrites: Writes[SettingsUpdateResponse] = Json.valueWrites[SettingsUpdateResponse]
  private implicit val settingsSetErrorWrites: Writes[SettingsSetError] = Json.writes[SettingsSetError]
  private implicit val settingsSetResponseWrites: Writes[SettingsSetResponse] = Json.writes[SettingsSetResponse]

  def deserializeGetRequest(input: JsValue): JsResult[JmapSettingsGet] = Json.fromJson[JmapSettingsGet](input)
  def deserializeSetRequest(input: JsValue): JsResult[SettingsSetRequest] = Json.fromJson[SettingsSetRequest](input)

  def serialize(response: JmapSettingsResponse): JsValue = Json.toJson(response)
  def serializeSetResponse(response: SettingsSetResponse): JsValue = Json.toJson(response)

}
