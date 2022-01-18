package com.linagora.tmail.james.jmap.model

import com.google.common.collect.{HashMultimap, Multimap, Multimaps}
import org.apache.james.jmap.api.model.AccountId
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import java.util.UUID
import scala.jdk.CollectionConverters.CollectionHasAsScala

case class EmailAddressContact (id: UUID, address: String) {
  def contains(part: String): Boolean = address.contains(part)
}

case class AccountEmailContact (accountId: String, id: UUID, address: String) {
  def this(accountId: AccountId, emailAddressContact: EmailAddressContact) {
    this(accountId.getIdentifier, emailAddressContact.id, emailAddressContact.address)
  }
}

trait EmailAddressContactSearchEngine {
  def index(accountId: AccountId, contact: EmailAddressContact): Publisher[Void]

  def autoComplete(accountId: AccountId, part: String): Publisher[EmailAddressContact]

}

class InMemoryEmailAddressContactSearchEngine extends EmailAddressContactSearchEngine {
  val emailList: Multimap[AccountId, EmailAddressContact] = Multimaps.synchronizedSetMultimap(HashMultimap.create())

  override def index(accountId: AccountId, address: EmailAddressContact): Publisher[Void] =
    SMono.fromCallable(() => emailList.put(accountId, address)).`then`()


  override def autoComplete(accountId: AccountId, part: String): Publisher[EmailAddressContact] =
      SFlux.fromIterable(emailList.get(accountId).asScala)
           .filter(_.address.contains(part))
}
