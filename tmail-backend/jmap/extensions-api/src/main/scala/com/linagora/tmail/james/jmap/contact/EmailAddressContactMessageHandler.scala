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
import org.apache.james.jmap.api.model.AccountId
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

sealed trait ContactMessageHandlerResult

case class Success() extends ContactMessageHandlerResult

case class Failure(error: Throwable) extends ContactMessageHandlerResult

class EmailAddressContactMessageHandler @Inject()(contactSearchEngine: EmailAddressContactSearchEngine) {

  def handler(message: EmailAddressContactMessage): Publisher[ContactMessageHandlerResult] =
    (message.scope match {
      case User => contactUserMessageHandler(message)
      case Domain => contactDomainMessageHandler(message)
    }).fold(SMono.error(_), handlerResult => handlerResult)

  private def contactUserMessageHandler(message: EmailAddressContactMessage): Either[IllegalArgumentException, SMono[ContactMessageHandlerResult]] =
    ContactOwner.asUsername(message.owner)
      .map(AccountId.fromUsername)
      .map(accountId => contactUserMessageHandler(accountId, message))

  private def contactDomainMessageHandler(message: EmailAddressContactMessage): Either[IllegalArgumentException, SMono[ContactMessageHandlerResult]] =
    ContactOwner.asDomain(message.owner)
      .map(domain => contactDomainMessageHandler(domain, message))

  private def contactDomainMessageHandler(domain: org.apache.james.core.Domain, message: EmailAddressContactMessage): SMono[ContactMessageHandlerResult] =
    (message.messageType match {
      case Addition => SMono.fromPublisher(contactSearchEngine.index(domain, MessageEntry.toContactField(message.entry))).`then`()
      case Update => SMono.fromPublisher(contactSearchEngine.update(domain, MessageEntry.toContactField(message.entry))).`then`()
      case Removal => SMono.fromPublisher(contactSearchEngine.delete(domain, message.entry.address)).`then`()
    })
      .`then`(SMono.just(Success()))
      .onErrorResume(error => SMono.just(Failure(error)))

  private def contactUserMessageHandler(accountId: AccountId, message: EmailAddressContactMessage): SMono[ContactMessageHandlerResult] =
    (message.messageType match {
      case Addition => SMono.fromPublisher(contactSearchEngine.index(accountId, MessageEntry.toContactField(message.entry))).`then`()
      case Update => SMono.fromPublisher(contactSearchEngine.update(accountId, MessageEntry.toContactField(message.entry))).`then`()
      case Removal => SMono.fromPublisher(contactSearchEngine.delete(accountId, message.entry.address)).`then`()
    }).`then`(SMono.just(Success()))
      .onErrorResume(error => SMono.just(Failure(error)))

}
