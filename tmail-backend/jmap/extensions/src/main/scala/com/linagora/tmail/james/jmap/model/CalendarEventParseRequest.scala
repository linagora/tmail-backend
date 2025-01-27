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

import org.apache.james.jmap.core.{AccountId, Properties}
import org.apache.james.jmap.mail.MDNParseRequest.MAXIMUM_NUMBER_OF_BLOB_IDS
import org.apache.james.jmap.mail.{BlobIds, RequestTooLargeException}
import org.apache.james.jmap.method.WithAccountId

import scala.language.implicitConversions

case class CalendarEventParseRequest(accountId: AccountId,
                                     blobIds: BlobIds,
                                     properties: Option[Properties]) extends WithAccountId {
  def validate: Either[RequestTooLargeException, CalendarEventParseRequest] =
    if (blobIds.value.length > MAXIMUM_NUMBER_OF_BLOB_IDS) {
      Left(RequestTooLargeException("The number of ids requested by the client exceeds the maximum number the server is willing to process in a single method call"))
    } else {
      scala.Right(this)
    }
}
