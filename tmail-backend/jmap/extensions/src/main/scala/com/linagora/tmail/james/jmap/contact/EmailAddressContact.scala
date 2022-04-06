package com.linagora.tmail.james.jmap.contact

import java.nio.charset.StandardCharsets
import java.util.UUID

import com.google.common.collect.{HashMultimap, Multimap, Multimaps}
import com.google.inject.{AbstractModule, Scopes}
import org.apache.james.core.{Domain, MailAddress, Username}
import org.apache.james.events.Event
import org.apache.james.events.Event.EventId
import org.apache.james.jmap.api.model.AccountId
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.OptionConverters._

case class InMemoryEmailAddressContactSearchEngineModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[InMemoryEmailAddressContactSearchEngine]).in(Scopes.SINGLETON)

    bind(classOf[EmailAddressContactSearchEngine]).to(classOf[InMemoryEmailAddressContactSearchEngine])
  }
}

object EmailAddressContact {
  private def computeId(mailAddress: MailAddress): UUID = UUID.nameUUIDFromBytes(mailAddress.asString().getBytes(StandardCharsets.UTF_8))

  def of(fields: ContactFields): EmailAddressContact = EmailAddressContact(computeId(fields.address), fields)
}

case class EmailAddressContact(id: UUID, fields: ContactFields)

case class ContactFields(address: MailAddress, firstname: String = "", surname: String = "") {
  def contains(part: String): Boolean =
    address.asString().contains(part) || firstname.contains(part) || surname.contains(part)
}

trait EmailAddressContactSearchEngine {
  def index(accountId: AccountId, fields: ContactFields): Publisher[EmailAddressContact]

  def index(domain: Domain, fields: ContactFields): Publisher[EmailAddressContact]

  def delete(accountId: AccountId, mailAddress: MailAddress): Publisher[Void]

  def delete(domain: Domain, mailAddress: MailAddress): Publisher[Void]

  def autoComplete(accountId: AccountId, part: String): Publisher[EmailAddressContact]
}

trait TmailEvent extends Event

trait TmailContactUserEvent extends TmailEvent

case class TmailContactUserAddedEvent(eventId: EventId, username: Username, contact: ContactFields) extends TmailContactUserEvent {
  override def getUsername: Username = username

  override def isNoop: Boolean = false

  override def getEventId: EventId = eventId
}

class InMemoryEmailAddressContactSearchEngine extends EmailAddressContactSearchEngine {
  val emailList: Multimap[AccountId, EmailAddressContact] = Multimaps.synchronizedSetMultimap(HashMultimap.create())
  val domainList: Multimap[Domain, EmailAddressContact] = Multimaps.synchronizedSetMultimap(HashMultimap.create())

  override def index(accountId: AccountId, fields: ContactFields): Publisher[EmailAddressContact] =
    index(accountId, EmailAddressContact.of(fields))

  private def index(accountId: AccountId, addressContact: EmailAddressContact) =
    SMono.fromCallable(() => emailList.put(accountId, addressContact))
      .`then`(SMono.just(addressContact))

  override def index(domain: Domain, fields: ContactFields): Publisher[EmailAddressContact] =
    index(domain, EmailAddressContact.of(fields))

  private def index(domain: Domain, addressContact: EmailAddressContact): Publisher[EmailAddressContact] =
    SMono.fromCallable(() => domainList.put(domain, addressContact))
      .`then`(SMono.just(addressContact))

  override def delete(accountId: AccountId, mailAddress: MailAddress): Publisher[Void] =
    getContact(accountId, mailAddress)
      .map(contact => emailList.remove(accountId, contact))
      .`then`()

  private def getContact(accountId: AccountId, mailAddress: MailAddress): SMono[EmailAddressContact] =
    SFlux.fromIterable(emailList.get(accountId).asScala)
      .filter(_.fields.address.equals(mailAddress))
      .singleOrEmpty()

  override def delete(domain: Domain, mailAddress: MailAddress): Publisher[Void] =
    getDomainContact(domain, mailAddress)
      .map(contact => domainList.remove(domain, contact))
      .`then`()

  private def getDomainContact(domain: Domain, mailAddress: MailAddress): SMono[EmailAddressContact] =
    SFlux.fromIterable(domainList.get(domain).asScala)
      .filter(_.fields.address.equals(mailAddress))
      .singleOrEmpty()

  override def autoComplete(accountId: AccountId, part: String): Publisher[EmailAddressContact] = {
    val maybeDomain: Option[Domain] = Username.of(accountId.getIdentifier).getDomainPart.toScala
    SFlux.concat(
      maybeDomain.map(domain => SFlux.fromIterable(domainList.get(domain).asScala)).getOrElse(SFlux.empty),
      SFlux.fromIterable(emailList.get(accountId).asScala))
      .filter(lowerCaseContact(_).fields.contains(part.toLowerCase))
      .sort(Ordering.by[EmailAddressContact, String](contact => contact.fields.address.asString))
  }

  private def lowerCaseContact(contact: EmailAddressContact): EmailAddressContact =
    EmailAddressContact(contact.id, ContactFields(address = new MailAddress(contact.fields.address.asString().toLowerCase),
      firstname = contact.fields.firstname.toLowerCase, surname = contact.fields.surname.toLowerCase))
}
