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

package com.linagora.tmail.james.jmap.team.mailboxes

import com.linagora.tmail.james.jmap.contact.{ContactFields, EmailAddressContactSearchEngine}
import com.linagora.tmail.team.{TeamMailbox, TeamMailboxCallback}
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class TeamMailboxAutocompleteCallback @Inject()(contactSearchEngine: EmailAddressContactSearchEngine) extends TeamMailboxCallback {

  def teamMailboxAdded(teamMailbox: TeamMailbox): Publisher[Void] =
    SMono.fromPublisher(contactSearchEngine.index(teamMailbox.domain, ContactFields(address = teamMailbox.asMailAddress,
      firstname = teamMailbox.mailboxName.asString())))
      .`then`()

  def teamMailboxRemoved(teamMailbox: TeamMailbox): Publisher[Void] =
    SMono.fromPublisher(contactSearchEngine.delete(teamMailbox.domain, teamMailbox.asMailAddress))
}
