/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

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
