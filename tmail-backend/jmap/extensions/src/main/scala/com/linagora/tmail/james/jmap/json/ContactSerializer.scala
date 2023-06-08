package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.{Contact, ContactAutocompleteRequest, ContactAutocompleteResponse, ContactFilter, ContactFirstname, ContactId, ContactSurname, ContactText}
import org.apache.james.jmap.core.LimitUnparsed
import play.api.libs.json.{JsError, JsObject, JsResult, JsString, JsSuccess, JsValue, Json, OWrites, Reads, Writes}

class ContactSerializer {
  private implicit val contactTextReads: Reads[ContactText] = {
    case JsString(value) => JsSuccess(ContactText(value))
    case _ => JsError("Expecting a JsString to be representing an autocomplete text search")
  }

  private implicit val contactFilterReads: Reads[ContactFilter] =  {
    case JsObject(underlying) => {
      val unsupported: collection.Set[String] = underlying.keySet.diff(ContactFilter.SUPPORTED)
      if (unsupported.nonEmpty) {
        JsError(s"These '${unsupported.mkString("[", ", ", "]")}' was unsupported filter options")
      } else {
        Json.reads[ContactFilter].reads(JsObject(underlying))
      }
    }
    case jsValue: JsValue => Json.reads[ContactFilter].reads(jsValue)
  }

  private implicit val limitUnparsedReads: Reads[LimitUnparsed] = Json.valueReads[LimitUnparsed]

  private implicit val contactIdWrites: Writes[ContactId] = Json.valueWrites[ContactId]
  private implicit val contactFirstnameWrites: Writes[ContactFirstname] = Json.valueWrites[ContactFirstname]
  private implicit val contactSurnameWrites: Writes[ContactSurname] = Json.valueWrites[ContactSurname]

  private implicit val contactWrites: Writes[Contact] = Json.writes[Contact]

  private implicit val contactAutocompleteRequestReads: Reads[ContactAutocompleteRequest] = Json.reads[ContactAutocompleteRequest]

  private implicit val contactAutocompleteResponseWrites: OWrites[ContactAutocompleteResponse] = Json.writes[ContactAutocompleteResponse]

  def deserialize(input: JsValue): JsResult[ContactAutocompleteRequest] = Json.fromJson[ContactAutocompleteRequest](input)

  def serialize(contactAutocompleteResponse: ContactAutocompleteResponse): JsObject = Json.toJsObject(contactAutocompleteResponse)
}
