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

import eu.timepit.refined.auto._
import org.apache.james.jmap.core.{AccountId, UuidState}
import org.apache.james.mailbox.inmemory.{InMemoryId, InMemoryMessageId}
import org.apache.james.mailbox.model.MessageId

object Fixture {
  lazy val ACCOUNT_ID: AccountId = AccountId("aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8")
  lazy val STATE: UuidState = UuidState.fromString("6e0dd59d-660e-4d9b-b22f-0354479f47b4")
  lazy val MESSAGE_ID_FACTORY: MessageId.Factory = new InMemoryMessageId.Factory
  lazy val MAILBOX_ID_FACTORY: InMemoryId.Factory = new InMemoryId.Factory
}
