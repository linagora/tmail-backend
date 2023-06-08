package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.contact.{ContactOwner, EmailAddressContactMessage, MessageEntry, TmailContactMessageScope, TmailContactMessageType}
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{JsError, JsPath, JsResult, JsString, JsSuccess, JsValue, Json, Reads}

import scala.util.{Failure, Success, Try}

object EmailAddressContactMessageSerializer {

  private implicit val contactMessageTypeReads: Reads[TmailContactMessageType] = {
    case JsString(value) => TmailContactMessageType.from(value)
      .fold(error => JsError(error.getMessage),
        messageType => JsSuccess(messageType))
    case _ => JsError("`type` needs to be represented by a string")
  }

  private implicit val contactMessageScopeReads: Reads[TmailContactMessageScope] = {
    case JsString(value) => TmailContactMessageScope.from(value)
      .fold(error => JsError(error.getMessage),
        scope => JsSuccess(scope))
    case _ => JsError("`scope` needs to be represented by a string")
  }

  private implicit val contactOwnerReads: Reads[ContactOwner] = Json.valueReads[ContactOwner]

  private implicit val messageEntryReads: Reads[MessageEntry] = Json.reads[MessageEntry]

  private implicit val emailAddressContactMessageReads: Reads[EmailAddressContactMessage] = (
    (JsPath \ "type").read[TmailContactMessageType] and
      (JsPath \ "scope").read[TmailContactMessageScope] and
      (JsPath \ "owner").read[ContactOwner] and
      (JsPath \ "entry").read[MessageEntry]
    ) (EmailAddressContactMessage.apply _)

  def deserializeEmailAddressContactMessage(input: JsValue): JsResult[EmailAddressContactMessage] = Json.fromJson[EmailAddressContactMessage](input)

  def deserializeEmailAddressContactMessageAsJava(input: String): EmailAddressContactMessage =
    Try(Json.parse(input))
      .map(jsValue => deserializeEmailAddressContactMessage(jsValue) match {
        case JsError(errors) => throw new IllegalArgumentException("Can not deserialize. " + errors.toString())
        case JsSuccess(value, _) => value
      })
    match {
      case Success(value) => value
      case Failure(exception) => throw new IllegalArgumentException("Input is not a json. " + exception.getMessage)
    }
}
