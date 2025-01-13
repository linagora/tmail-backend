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

import com.linagora.tmail.james.jmap.publicAsset.ImageContentType.ImageContentType
import com.linagora.tmail.james.jmap.publicAsset.{PublicAssetId, PublicURI}
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{AccountId, UuidState}
import org.apache.james.jmap.method.WithAccountId

case class PublicAssetGetRequest(accountId: AccountId,
                                 ids: Option[Set[Id]] = None) extends WithAccountId

case class PublicAssetGetResponse(accountId: AccountId,
                                  state: UuidState,
                                  list: Seq[PublicAssetDTO],
                                  notFound: Seq[String])

case class PublicAssetDTO(id: PublicAssetId,
                          publicURI: PublicURI,
                          size: Size,
                          contentType: ImageContentType,
                          identityIds: Map[IdentityId, Boolean] = Map.empty)