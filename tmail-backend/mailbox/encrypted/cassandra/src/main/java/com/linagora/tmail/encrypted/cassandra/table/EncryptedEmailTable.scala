package com.linagora.tmail.encrypted.cassandra.table

object EncryptedEmailTable {
  val TABLE_NAME: String = "encrypted_email_content"
  val MESSAGE_ID: String = "message_id"
  val ENCRYPTED_PREVIEW: String = "encrypted_preview"
  val ENCRYPTED_HTML: String = "encrypted_html"
  val HAS_ATTACHMENT: String = "has_attachment"
  val ENCRYPTED_ATTACHMENT_METADATA: String = "encrypted_attachment_metadata"
  val POSITION_BLOB_ID_MAPPING: String = "position_blob_id_mapping"
}
