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

package com.linagora.tmail.james.jmap.model

import org.apache.james.jmap.core.{AccountId, SetError}
import org.apache.james.jmap.method.{SetRequest, WithAccountId}
import play.api.libs.json.JsValue

case class UnauthenticatedBlobAccessSetRequest(accountId: AccountId,
                                               create: Option[Map[String, JsValue]],
                                               update: Option[Map[String, JsValue]],
                                               destroy: Option[Seq[String]]) extends WithAccountId with SetRequest {
  override def idCount: Int =
    create.fold(0)(_.size) +
      update.fold(0)(_.size) +
      destroy.fold(0)(_.size)
}

case class UnauthenticatedBlobAccessCreationResponse(token: String)

case class UnauthenticatedBlobAccessSetResponse(accountId: AccountId,
                                                created: Option[Map[String, UnauthenticatedBlobAccessCreationResponse]],
                                                notCreated: Option[Map[String, SetError]],
                                                notUpdated: Option[Map[String, SetError]],
                                                notDestroyed: Option[Map[String, SetError]])
