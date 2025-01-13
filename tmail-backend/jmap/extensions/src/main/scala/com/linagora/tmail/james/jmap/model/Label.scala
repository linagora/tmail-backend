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

import org.apache.james.jmap.core.{AccountId, Properties, UuidState}
import org.apache.james.jmap.method.WithAccountId

case class LabelGetRequest(accountId: AccountId,
                           ids: Option[LabelIds],
                           properties: Option[Properties]) extends WithAccountId {
  def validateProperties: Either[IllegalArgumentException, Properties] =
    properties match {
      case None => Right(Label.allProperties)
      case Some(value) =>
        value -- Label.allProperties match {
          case invalidProperties if invalidProperties.isEmpty() => Right(value ++ Label.idProperty)
          case invalidProperties: Properties => Left(new IllegalArgumentException(s"The following properties [${invalidProperties.format()}] do not exist."))
        }
    }
}

object LabelGetResponse {
  def from(accountId: AccountId, state: UuidState, list: Seq[Label], requestIds: Option[LabelIds]): LabelGetResponse =
    requestIds match {
      case None => LabelGetResponse(accountId, state, list)
      case Some(value) =>
        LabelGetResponse(
          accountId = accountId,
          state = state,
          list = list.filter(label => value.list.contains(label.id.asUnparsedLabelId)),
          notFound = value.list.diff(list.map(_.id.asUnparsedLabelId)))
    }
}

case class LabelGetResponse(accountId: AccountId,
                            state: UuidState,
                            list: Seq[Label],
                            notFound: Seq[UnparsedLabelId] = Seq())