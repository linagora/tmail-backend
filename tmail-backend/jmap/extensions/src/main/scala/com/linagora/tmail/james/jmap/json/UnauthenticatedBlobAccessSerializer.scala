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

package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.{UnauthenticatedBlobAccessCreationResponse, UnauthenticatedBlobAccessSetRequest, UnauthenticatedBlobAccessSetResponse}
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.json.mapWrites
import play.api.libs.json.{JsValue, Json, Reads, Writes}

object UnauthenticatedBlobAccessSerializer {
  private implicit val creationResponseWrites: Writes[UnauthenticatedBlobAccessCreationResponse] =
    Json.writes[UnauthenticatedBlobAccessCreationResponse]
  private implicit val stringCreationResponseMapWrites: Writes[Map[String, UnauthenticatedBlobAccessCreationResponse]] =
    mapWrites[String, UnauthenticatedBlobAccessCreationResponse](identity, creationResponseWrites)
  private implicit val stringSetErrorMapWrites: Writes[Map[String, SetError]] =
    mapWrites[String, SetError](identity, setErrorWrites)

  private implicit val setRequestReads: Reads[UnauthenticatedBlobAccessSetRequest] =
    Json.reads[UnauthenticatedBlobAccessSetRequest]
  private implicit val setResponseWrites: Writes[UnauthenticatedBlobAccessSetResponse] =
    Json.writes[UnauthenticatedBlobAccessSetResponse]

  def deserializeSetRequest(input: JsValue) =
    Json.fromJson[UnauthenticatedBlobAccessSetRequest](input)

  def serializeSetResponse(response: UnauthenticatedBlobAccessSetResponse): JsValue =
    Json.toJson(response)(setResponseWrites)
}
