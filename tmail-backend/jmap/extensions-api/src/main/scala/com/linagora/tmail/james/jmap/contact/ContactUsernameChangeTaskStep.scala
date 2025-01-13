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

package com.linagora.tmail.james.jmap.contact

import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.user.api.UsernameChangeTaskStep
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

class ContactUsernameChangeTaskStep @Inject()(contactSearchEngine: EmailAddressContactSearchEngine) extends UsernameChangeTaskStep {
  override def name(): UsernameChangeTaskStep.StepName = new UsernameChangeTaskStep.StepName("ContactUsernameChangeTaskStep")

  override def priority(): Int = 5

  override def changeUsername(oldUsername: Username, newUsername: Username): Publisher[Void] = {
    val oldAccountId: AccountId = AccountId.fromUsername(oldUsername)
    val newAccountId: AccountId = AccountId.fromUsername(newUsername)

    SFlux.fromPublisher(contactSearchEngine.list(oldAccountId))
      .flatMap(contact => SMono.fromPublisher(contactSearchEngine.index(newAccountId, contact.fields))
        .`then`(SMono.fromPublisher(contactSearchEngine.delete(oldAccountId, contact.fields.address))))
      .`then`()
  }
}
