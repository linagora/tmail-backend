package com.linagora.tmail.james.jmap.model

import org.apache.james.jmap.api.model.AccountId
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import java.util.UUID

case class EmailAddressContact (id: UUID, address: String) {
  def isContain(part: String): Boolean = {
    address.contains(part);
  }
}

trait EmailAddressContactSearchEngine {
  def index(accountId: AccountId, contact: EmailAddressContact): Publisher[Unit]

  def autoComplete(accountId: AccountId, part: String): Publisher[EmailAddressContact]

}

class InMemoryEmailAddressContactSearchEngine extends EmailAddressContactSearchEngine {
  var emailList: Map[AccountId, Set[EmailAddressContact]] = Map()

  override def index(accountId: AccountId, address: EmailAddressContact): Publisher[Unit] = {
    var mailListPerAcc: Set[EmailAddressContact] = Set()
    if (emailList.contains(accountId)) {
      mailListPerAcc = emailList.apply(accountId)
    }
    mailListPerAcc = mailListPerAcc + address
    emailList = emailList + (accountId -> mailListPerAcc)
    SFlux.fromIterable(emailList).`then`()
  }

  override def autoComplete(accountId: AccountId, part: String): Publisher[EmailAddressContact] = {
    var addressList: Set[EmailAddressContact] = Set()
    if (emailList.contains(accountId)) {
      addressList = emailList.apply(accountId)
    }
    val fullList: SFlux[EmailAddressContact] = SFlux.fromIterable(addressList)
    fullList.filter(e => e.address.contains(part))
  }
}
