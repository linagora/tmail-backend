package com.linagora.tmail.james.jmap.contact

import java.nio.charset.StandardCharsets
import java.util.UUID

import com.google.common.collect.{HashBasedTable, ImmutableList, Table, Tables}
import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import org.apache.james.core.{Domain, MailAddress, Username}
import org.apache.james.events.Event
import org.apache.james.events.Event.EventId
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.user.api.{DeleteUserDataTaskStep, UsernameChangeTaskStep}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.OptionConverters._

case class InMemoryEmailAddressContactSearchEngineModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[InMemoryEmailAddressContactSearchEngine]).in(Scopes.SINGLETON)

    bind(classOf[EmailAddressContactSearchEngine]).to(classOf[InMemoryEmailAddressContactSearchEngine])

    Multibinder.newSetBinder(binder(), classOf[UsernameChangeTaskStep])
      .addBinding()
      .to(classOf[ContactUsernameChangeTaskStep])
    Multibinder.newSetBinder(binder(), classOf[DeleteUserDataTaskStep])
      .addBinding()
      .to(classOf[ContactUserDeletionTaskStep])
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

case class ContactNotFoundException(mailAddress: MailAddress) extends RuntimeException {
  override def getMessage: String = s"The contact ${mailAddress.asString()} can not be found"
}

trait EmailAddressContactSearchEngine {
  def index(accountId: AccountId, fields: ContactFields): Publisher[EmailAddressContact]

  def index(domain: Domain, fields: ContactFields): Publisher[EmailAddressContact]

  def update(accountId: AccountId, updatedFields: ContactFields): Publisher[EmailAddressContact]

  def update(domain: Domain, updatedFields: ContactFields): Publisher[EmailAddressContact]

  def delete(accountId: AccountId, mailAddress: MailAddress): Publisher[Void]

  def delete(domain: Domain, mailAddress: MailAddress): Publisher[Void]

  def autoComplete(accountId: AccountId, part: String, limit: Int = 256): Publisher[EmailAddressContact]

  def list(accountId: AccountId): Publisher[EmailAddressContact]

  def list(domain: Domain): Publisher[EmailAddressContact]

  def listDomainsContacts(): Publisher[EmailAddressContact]

  def get(accountId: AccountId, mailAddress: MailAddress): Publisher[EmailAddressContact]

  def get(domain: Domain, mailAddress: MailAddress): Publisher[EmailAddressContact]
}

trait TmailEvent extends Event

trait TmailContactUserEvent extends TmailEvent

case class TmailContactUserAddedEvent(eventId: EventId, username: Username, contact: ContactFields) extends TmailContactUserEvent {
  override def getUsername: Username = username

  override def isNoop: Boolean = false

  override def getEventId: EventId = eventId
}

class InMemoryEmailAddressContactSearchEngine extends EmailAddressContactSearchEngine {
  private val userContactList: Table[AccountId, MailAddress, EmailAddressContact] = Tables.synchronizedTable(HashBasedTable.create())
  private val domainContactList: Table[Domain, MailAddress, EmailAddressContact] = Tables.synchronizedTable(HashBasedTable.create())

  override def index(accountId: AccountId, fields: ContactFields): Publisher[EmailAddressContact] =
    index(accountId, EmailAddressContact.of(fields))

  private def index(accountId: AccountId, addressContact: EmailAddressContact) =
    SFlux.fromIterable(domainContactList.values().asScala)
      .filter(contact => contact.fields.address.equals(addressContact.fields.address))
      .count()
      .map(hits => hits == 0)
      .flatMap(_ => SMono.fromCallable(() => userContactList.put(accountId, addressContact.fields.address, addressContact)))
      .`then`(SMono.just(addressContact))

  override def index(domain: Domain, fields: ContactFields): Publisher[EmailAddressContact] =
    index(domain, EmailAddressContact.of(fields))

  private def index(domain: Domain, addressContact: EmailAddressContact): Publisher[EmailAddressContact] =
    SMono.fromCallable(() => domainContactList.put(domain, addressContact.fields.address, addressContact))
      .`then`(SMono.just(addressContact))

  override def update(accountId: AccountId, updatedFields: ContactFields): Publisher[EmailAddressContact] =
    SMono.fromCallable(() => userContactList.put(accountId, updatedFields.address, EmailAddressContact.of(updatedFields)))

  override def update(domain: Domain, updatedFields: ContactFields): Publisher[EmailAddressContact] =
    SMono.fromCallable(() => domainContactList.put(domain, updatedFields.address, EmailAddressContact.of(updatedFields)))

  override def delete(accountId: AccountId, mailAddress: MailAddress): Publisher[Void] =
    SMono.fromCallable(() => userContactList.remove(accountId, mailAddress))
      .`then`()

  override def delete(domain: Domain, mailAddress: MailAddress): Publisher[Void] =
    SMono.fromCallable(() => domainContactList.remove(domain, mailAddress))
      .`then`()

  override def autoComplete(accountId: AccountId, part: String, limit: Int): Publisher[EmailAddressContact] = {
    val maybeDomain: Option[Domain] = Username.of(accountId.getIdentifier).getDomainPart.toScala
    SFlux.concat(
      maybeDomain.map(domain => SFlux.fromIterable(domainContactList.row(domain).values().asScala)).getOrElse(SFlux.empty),
      SFlux.fromIterable(userContactList.row(accountId).values().asScala))
      .filter(lowerCaseContact(_).fields.contains(part.toLowerCase))
      .sort(Ordering.by[EmailAddressContact, String](contact => contact.fields.address.asString))
      .distinct(_.id)
      .take(limit)
      .map(lowerCaseEmailAddress)
  }

  private def lowerCaseContact(contact: EmailAddressContact): EmailAddressContact =
    EmailAddressContact(contact.id, ContactFields(address = new MailAddress(contact.fields.address.asString().toLowerCase),
      firstname = contact.fields.firstname.toLowerCase, surname = contact.fields.surname.toLowerCase))

  private def lowerCaseEmailAddress(contact: EmailAddressContact): EmailAddressContact =
    EmailAddressContact(contact.id, ContactFields(address = new MailAddress(contact.fields.address.asString().toLowerCase),
      firstname = contact.fields.firstname, surname = contact.fields.surname))

  override def list(accountId: AccountId): Publisher[EmailAddressContact] =
    SFlux.fromIterable(ImmutableList.copyOf(userContactList.row(accountId).values()).asScala)

  override def list(domain: Domain): Publisher[EmailAddressContact] =
    SFlux.fromIterable(ImmutableList.copyOf(domainContactList.row(domain).values()).asScala)

  override def listDomainsContacts(): Publisher[EmailAddressContact] =
    SFlux.fromIterable(ImmutableList.copyOf(domainContactList.values()).asScala)

  override def get(accountId: AccountId, mailAddress: MailAddress): Publisher[EmailAddressContact] =
    SFlux.fromIterable(ImmutableList.copyOf(userContactList.row(accountId).values()).asScala)
      .filter(lowerCaseContact(_).fields.address.equals(mailAddress))
      .singleOrEmpty()
      .switchIfEmpty(SMono.error(ContactNotFoundException(mailAddress)))

  override def get(domain: Domain, mailAddress: MailAddress): Publisher[EmailAddressContact] =
    SFlux.fromIterable(ImmutableList.copyOf(domainContactList.row(domain).values()).asScala)
      .filter(lowerCaseContact(_).fields.address.equals(mailAddress))
      .singleOrEmpty()
      .switchIfEmpty(SMono.error(ContactNotFoundException(mailAddress)))
}
