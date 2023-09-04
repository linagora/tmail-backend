package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.JmapSettingsEntry.JmapSettingsId
import com.linagora.tmail.james.jmap.model.{JmapSettingsEntry, JmapSettingsGet, JmapSettingsResponse}
import com.linagora.tmail.james.jmap.settings.JmapSettings.{JmapSettingsKey, JmapSettingsValue}
import org.apache.james.jmap.core.UuidState
import org.apache.james.jmap.json.mapWrites
import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{JsPath, JsResult, JsValue, Json, Reads, Writes}

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

  def deserializeGetRequest(input: JsValue): JsResult[JmapSettingsGet] = Json.fromJson[JmapSettingsGet](input)

  def serialize(response: JmapSettingsResponse): JsValue = Json.toJson(response)

}
