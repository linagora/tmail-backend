package com.linagora.tmail.james.jmap.contact

import com.google.common.collect.{HashMultimap, Multimap, Multimaps}
import org.apache.james.core.{Domain, MailAddress, Username}
import org.apache.james.jmap.api.model.AccountId
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.jdk.CollectionConverters.CollectionHasAsScala

object EmailAddressContact {
  private def computeId(mailAddress: MailAddress): UUID = UUID.nameUUIDFromBytes(mailAddress.asString().getBytes(StandardCharsets.UTF_8))

  def of(address: MailAddress): EmailAddressContact = EmailAddressContact(computeId(address), address)
}

case class EmailAddressContact(id: UUID, address: MailAddress) {
  def contains(part: String): Boolean = address.asString().contains(part)
}

case class AccountEmailContact(accountId: String, id: UUID, address: MailAddress) {
  def this(accountId: AccountId, emailAddressContact: EmailAddressContact) =
    this(accountId.getIdentifier, emailAddressContact.id, emailAddressContact.address)
}

trait EmailAddressContactSearchEngine {
  def index(accountId: AccountId, address: MailAddress): Publisher[EmailAddressContact]

  def index(domain: Domain, address: MailAddress): Publisher[EmailAddressContact]

  def autoComplete(accountId: AccountId, part: String): Publisher[EmailAddressContact]

}

class InMemoryEmailAddressContactSearchEngine extends EmailAddressContactSearchEngine {
  val emailList: Multimap[AccountId, EmailAddressContact] = Multimaps.synchronizedSetMultimap(HashMultimap.create())
  val domainList: Multimap[Domain, EmailAddressContact] = Multimaps.synchronizedSetMultimap(HashMultimap.create())

  override def index(accountId: AccountId, address: MailAddress): Publisher[EmailAddressContact] =
    index(accountId, EmailAddressContact.of(address))

  private def index(accountId: AccountId, addressContact: EmailAddressContact) =
    SMono.fromCallable(() => emailList.put(accountId, addressContact))
      .`then`(SMono.just(addressContact))

  override def index(domain: Domain, address: MailAddress): Publisher[EmailAddressContact] =
    index(domain, EmailAddressContact.of(address))

  private def index(domain: Domain, addressContact: EmailAddressContact): Publisher[EmailAddressContact] =
    SMono.fromCallable(() => domainList.put(domain, addressContact))
      .`then`(SMono.just(addressContact))

  override def autoComplete(accountId: AccountId, part: String): Publisher[EmailAddressContact] = {
    val domain = Username.of(accountId.getIdentifier).getDomainPart.get // TODO: evaluate optional
    SFlux.concat(
      SFlux.fromIterable(domainList.get(domain).asScala),
      SFlux.fromIterable(emailList.get(accountId).asScala)
    ).filter(_.contains(part))
  }
}
