package com.linagora.tmail.encrypted

import play.api.libs.json._

object AttachmentMetaDataSerializer {

  private implicit val attachmentMetadataWrites: Writes[AttachmentMetadata] = Json.writes[AttachmentMetadata]

  def serialize(attachmentMetadata: AttachmentMetadata): JsValue = Json.toJson(attachmentMetadata)

  def serializeList(listAttachmentMetadata: List[AttachmentMetadata]): JsValue = Json.toJson(listAttachmentMetadata)

}
