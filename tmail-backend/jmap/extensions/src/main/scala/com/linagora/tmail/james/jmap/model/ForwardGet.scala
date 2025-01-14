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

import eu.timepit.refined.auto._
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{AccountId, Id, Properties, UuidState}
import org.apache.james.jmap.method.WithAccountId

case class UnparsedForwardId(id: Id)

case class ForwardIds(value: List[UnparsedForwardId])

case class ForwardGetRequest(accountId: AccountId,
                             ids: Option[ForwardIds],
                             properties: Option[Properties]) extends WithAccountId

case class ForwardNotFound(value: Set[UnparsedForwardId]) {
  def merge(other: ForwardNotFound): ForwardNotFound = ForwardNotFound(this.value ++ other.value)
}

case object ForwardId {
  val asString: String = "singleton"
}
case class LocalCopy(value: Boolean) extends AnyVal
case class Forward(value: MailAddress)

object Forwards {
  val FORWARD_ID: Id = Id.validate(ForwardId.asString).toOption.get
  val UNPARSED_SINGLETON: UnparsedForwardId = UnparsedForwardId(FORWARD_ID)

  def asRfc8621(forwards: List[MailAddress], mailAddress: MailAddress): Forwards = Forwards(
    id = FORWARD_ID,
    localCopy = LocalCopy(forwards.isEmpty || forwards.contains(mailAddress)),
    forwards = forwards.filter(!_.equals(mailAddress))
      .map(Forward)
  )

  val allProperties: Properties = Properties("id", "localCopy", "forwards")
  val idProperty: Properties = Properties("id")

  def propertiesFiltered(requestedProperties: Properties) : Properties = idProperty ++ requestedProperties
}

case class Forwards(id: Id,
                    localCopy: LocalCopy,
                    forwards: List[Forward])

case class ForwardGetResponse(accountId: AccountId,
                              state: UuidState,
                              list: List[Forwards],
                              notFound: ForwardNotFound)


