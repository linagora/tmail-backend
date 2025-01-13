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

import java.util.UUID

import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.Limit.Limit
import org.apache.james.jmap.core.{AccountId, LimitUnparsed}
import org.apache.james.jmap.method.WithAccountId

case class ContactAutocompleteRequest(accountId: AccountId,
                                      filter: ContactFilter,
                                      limit: Option[LimitUnparsed]) extends WithAccountId

case class ContactText(value: String)

object ContactFilter{
  val SUPPORTED: Set[String] = Set("text")
}
case class ContactFilter(text: ContactText)

case class ContactAutocompleteResponse(accountId: AccountId,
                                       list: Seq[Contact],
                                       limit: Option[Limit])

case class ContactId(value: UUID)
case class ContactFirstname(value: String)
case class ContactSurname(value: String)
case class Contact(id: ContactId,
                   emailAddress: MailAddress,
                   firstname: ContactFirstname,
                   surname: ContactSurname)