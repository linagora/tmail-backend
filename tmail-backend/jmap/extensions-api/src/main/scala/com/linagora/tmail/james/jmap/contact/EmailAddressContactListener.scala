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
import org.apache.james.events.EventListener.ReactiveGroupEventListener
import org.apache.james.events.{Event, Group}
import org.apache.james.jmap.api.model.AccountId
import org.reactivestreams.Publisher
import org.slf4j.{Logger, LoggerFactory}
import reactor.core.scala.publisher.SMono

class EmailAddressContactListener @Inject()(contactSearchEngine: EmailAddressContactSearchEngine) extends ReactiveGroupEventListener {
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[EmailAddressContactListener])

  override def getDefaultGroup: Group = EmailAddressContactListenerGroup()

  override def reactiveEvent(event: Event): Publisher[Void] = {
    event match {
      case contactEvent: TmailContactUserAddedEvent => handlerContactUserAddedEvent(contactEvent)
      case _ => SMono.empty
    }
  }

  override def isHandling(event: Event): Boolean = event.isInstanceOf[TmailContactUserAddedEvent]

  private def handlerContactUserAddedEvent(event: TmailContactUserAddedEvent): Publisher[Void] =
    SMono.fromPublisher(contactSearchEngine.index(AccountId.fromUsername(event.username), event.contact))
      .doOnError(error => LOGGER.error("Error when indexing the new contact.", error))
      .`then`()
}

case class EmailAddressContactListenerGroup() extends Group {}