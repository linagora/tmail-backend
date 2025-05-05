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

import org.apache.james.jmap.core.UnsignedInt.UnsignedInt
import org.apache.james.jmap.core.{AccountId, SetError}
import org.apache.james.jmap.mail.UnparsedMailboxId
import org.apache.james.jmap.method.WithAccountId

case class MailboxClearRequest(accountId: AccountId,
                               mailboxId: UnparsedMailboxId) extends WithAccountId

case class MailboxClearResponse(accountId: AccountId,
                                totalDeletedMessagesCount: Option[UnsignedInt],
                                notCleared: Option[SetError] = None)
