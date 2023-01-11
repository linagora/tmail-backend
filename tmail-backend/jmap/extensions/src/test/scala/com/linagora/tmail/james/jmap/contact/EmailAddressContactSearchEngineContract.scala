package com.linagora.tmail.james.jmap.contact

import java.util.stream.IntStream

import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngineContract.{accountId, accountIdB, bigContactsNumber, contactEmptyNameFieldsA, contactEmptyNameFieldsB, contactFieldsA, contactFieldsB, contactFieldsFrench, domain, firstnameB, mailAddressA, otherContactEmptyNameFields, otherContactFields, otherContactFieldsWithUppercaseEmail, otherMailAddress, surnameB}
import org.apache.james.core.{Domain, MailAddress, Username}
import org.apache.james.jmap.api.model.AccountId
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.{SFlux, SMono}

object EmailAddressContactSearchEngineContract {
  private val domain: Domain = Domain.of("linagora.com")

  private val accountId: AccountId = AccountId.fromUsername(Username.fromLocalPartWithDomain("bob", domain))
  private val accountIdB: AccountId = AccountId.fromUsername(Username.fromLocalPartWithDomain("doraemon", domain))

  private val mailAddressA: MailAddress = new MailAddress("nobita@linagora.com")
  private val firstnameA: String = "John"
  private val surnameA: String = "Carpenter"
  private val contactEmptyNameFieldsA: ContactFields = ContactFields(mailAddressA)
  private val contactFieldsA: ContactFields = ContactFields(mailAddressA, firstnameA, surnameA)

  private val mailAddressB: MailAddress = new MailAddress("nobito@linagora.com")
  private val firstnameB: String = "Marie"
  private val surnameB: String = "Carpenter"
  private val contactEmptyNameFieldsB: ContactFields = ContactFields(mailAddressB)
  private val contactFieldsB: ContactFields = ContactFields(mailAddressB, firstnameB, surnameB)

  private val otherMailAddress: MailAddress = new MailAddress("nobitu@other.com")
  private val otherFirstname: String = "Johnny"
  private val otherSurname: String = "Carariepent"
  private val otherContactEmptyNameFields: ContactFields = ContactFields(otherMailAddress)
  private val otherContactFields: ContactFields = ContactFields(otherMailAddress, otherFirstname, otherSurname)

  private val otherMailAddressUpperCase: MailAddress = new MailAddress("JOHNDOE@OTHER.COM")
  private val otherContactFieldsWithUppercaseEmail: ContactFields = ContactFields(otherMailAddressUpperCase, otherFirstname, otherSurname)

  private val mailAddressRene: MailAddress = new MailAddress("dchloe@other.com")
  private val firstnameFrench: String = "Dené"
  private val surnameFrench: String = "Chloé"
  private val contactFieldsFrench: ContactFields = ContactFields(mailAddressRene, firstnameFrench, surnameFrench)

  private val bigContactsNumber: Int = 1000
}

trait EmailAddressContactSearchEngineContract {
  def testee(): EmailAddressContactSearchEngine

  def awaitDocumentsIndexed(query: QueryType, documentCount: Long): Unit

  @Test
  def shouldNotReturnDuplicatedContactAtReadTime(): Unit = {
    val mailAddress: MailAddress = new MailAddress("nobita@linagora.com")
    val contactFields: ContactFields = ContactFields(mailAddress, "John", "Carpenter")
    val duplicatedContactFields: ContactFields = ContactFields(mailAddress, "John Carpenter", "")
    SMono(testee().index(accountId, contactFields)).block()
    SMono(testee().index(domain, duplicatedContactFields)).block()

    // The duplicated domain contact should still be indexed cause others users could not have it yet
    awaitDocumentsIndexed(MatchAllQuery(), 2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, mailAddress.asString())).asJava().map(_.fields.address).collectList().block())
      .containsExactlyInAnyOrder(mailAddress)
  }

  @Test
  def givenDomainContactXExistedThenAutoCompleteShouldNotIndexDuplicatedUserContactXAtIndexTime(): Unit = {
    val mailAddress: MailAddress = new MailAddress("nobita@linagora.com")
    val contactFields: ContactFields = ContactFields(mailAddress, "John", "Carpenter")
    val duplicatedContactFields: ContactFields = ContactFields(mailAddress, "John Carpenter", "")

    // GIVEN that contact X is indexed in domain index
    SMono(testee().index(domain, contactFields)).block()
    awaitDocumentsIndexed(MatchAllQuery(), 1)

    // WHEN user send mail to contact X therefore contact X is trying to be indexed in user index
    SMono(testee().index(accountId, duplicatedContactFields)).block()
    Thread.sleep(500) // wait for the duplicated contact to be potentially indexed by ES

    // THEN the contact X should only be indexed once after all.
    awaitDocumentsIndexed(MatchAllQuery(), 1)
  }

  @Test
  def searchASCIICharactersShouldReturnMatchedFrenchName(): Unit = {
    SMono(testee().index(accountId, contactFieldsFrench)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 1)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "dene")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsFrench)
    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "chloe")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsFrench)
  }

  @Test
  def searchFrenchCharactersShouldReturnMatchedFrenchName(): Unit = {
    SMono(testee().index(accountId, contactFieldsFrench)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 1)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "dené")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsFrench)
    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "chloé")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsFrench)
  }

  @Test
  def indexShouldReturnMatched(): Unit = {
    SMono(testee().index(accountId, contactEmptyNameFieldsA)).block()
    SMono(testee().index(accountId, contactEmptyNameFieldsB)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "bit")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactEmptyNameFieldsA, contactEmptyNameFieldsB)
  }

  @Test
  def indexShouldReturnNoMatch(): Unit = {
    SMono(testee().index(accountId, contactEmptyNameFieldsA)).block()
    SMono(testee().index(accountId, contactEmptyNameFieldsB)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "dora")).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def indexShouldReturnEmpty(): Unit = {
    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "any")).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def indexWithDifferentAccount(): Unit = {
    SMono(testee().index(accountId, contactEmptyNameFieldsA)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 1)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountIdB, "bit")).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def indexDomainShouldReturnMatched(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "bit")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsA, contactFieldsB)
  }

  @Test
  def indexDomainShouldReturnNoMatch(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "dora")).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def indexShouldMatchDomainAndPersonalContacts(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()
    SMono(testee().index(accountId, otherContactEmptyNameFields)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 3)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "bit")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsA, contactFieldsB, otherContactEmptyNameFields)
  }

  @Test
  def indexShouldReturnFirstnameMatched(): Unit = {
    SMono(testee().index(accountId, contactFieldsA)).block()
    SMono(testee().index(accountId, contactFieldsB)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Mari")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsB)
  }

  @Test
  def indexShouldReturnSurnameMatched(): Unit = {
    SMono(testee().index(accountId, contactFieldsA)).block()
    SMono(testee().index(accountId, contactFieldsB)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Carpent")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsA, contactFieldsB)
  }

  @Test
  def indexShouldReturnNoNameMatch(): Unit = {
    SMono(testee().index(accountId, contactFieldsA)).block()
    SMono(testee().index(accountId, contactFieldsB)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "dora")).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def indexDomainShouldReturnFirstnameMatched(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Mari")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsB)
  }

  @Test
  def indexDomainShouldReturnSurnameMatched(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Carpent")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsA, contactFieldsB)
  }

  @Test
  def indexDomainShouldReturnNoNameMatch(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "dora")).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def indexShouldMatchFirstnameDomainAndPersonalContacts(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()
    SMono(testee().index(accountId, otherContactFields)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 3)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "John")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsA, otherContactFields)
  }

  @Test
  def indexShouldMatchLastnameDomainAndPersonalContacts(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()
    SMono(testee().index(accountId, otherContactFields)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 3)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Car")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsA, contactFieldsB, otherContactFields)
  }

  @Test
  def indexShouldMixMatchDomainAndPersonalContacts(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()
    SMono(testee().index(accountId, otherContactFields)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 3)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Car")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsA, contactFieldsB, otherContactFields)
  }

  @Test
  def firstnameAndSurnameAutoCompleteShouldSupportBothLowerCaseAndUpperCase(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()
    SMono(testee().index(accountId, otherContactFields)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 3)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "CAR")).asJava().map(_.fields).collectList().block())
        .containsExactlyInAnyOrder(contactFieldsA, contactFieldsB, otherContactFields)
      softly.assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "car")).asJava().map(_.fields).collectList().block())
        .containsExactlyInAnyOrder(contactFieldsA, contactFieldsB, otherContactFields)
    })
  }

  @Test
  def emailAutoCompleteShouldSupportBothLowerCaseAndUpperCase(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(accountId, otherContactFields)).block()
    SMono(testee().index(accountId, otherContactFieldsWithUppercaseEmail)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 3)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "nobita@linagora.com")).asJava().map(_.fields).collectList().block())
        .containsExactlyInAnyOrder(contactFieldsA)
      softly.assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "NOBITA@LINAGORA.COM")).asJava().map(_.fields).collectList().block())
        .containsExactlyInAnyOrder(contactFieldsA)

      softly.assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "nobitu@other.com")).asJava().map(_.fields).collectList().block())
        .containsExactlyInAnyOrder(otherContactFields)
      softly.assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "NOBITU@OTHER.COM")).asJava().map(_.fields).collectList().block())
        .containsExactlyInAnyOrder(otherContactFields)

      softly.assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "JOHNDOE@OTHER.COM")).asJava().map(_.fields).collectList().block())
        .containsExactlyInAnyOrder(otherContactFieldsWithUppercaseEmail)
      softly.assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "johndoe@other.com")).asJava().map(_.fields).collectList().block())
        .containsExactlyInAnyOrder(otherContactFieldsWithUppercaseEmail)
    })
  }

  @Test
  def deleteShouldDeletePersonalContact(): Unit = {
    SMono(testee().index(accountId, otherContactFields)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 1)

    SMono(testee().delete(accountId, otherMailAddress)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 0)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Car")).asJava().map(_.fields).collectList().block())
      .isEmpty()
  }

  @Test
  def deleteShouldDeleteDomainContact(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 1)

    SMono(testee().delete(domain, mailAddressA)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 0)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Car")).asJava().map(_.fields).collectList().block())
      .isEmpty()
  }

  @Test
  def deleteShouldNotDeleteOtherPersonalContact(): Unit = {
    SMono(testee().index(accountId, contactFieldsA)).block()
    SMono(testee().index(accountId, otherContactFields)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    SMono(testee().delete(accountId, otherMailAddress)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 1)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Car")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsA)
  }

  @Test
  def deleteShouldNotDeleteOtherDomainContact(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    SMono(testee().delete(domain, mailAddressA)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 1)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Car")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsB)
  }

  @Test
  def deletePersonalContactShouldBeIdempotent(): Unit = {
    assertThatCode(() => SMono(testee().delete(accountId, otherMailAddress)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def deleteDomainContactShouldBeIdempotent(): Unit = {
    assertThatCode(() => SMono(testee().delete(domain, mailAddressA)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def updatePersonalContactShouldSucceed(): Unit = {
    val updatedContact = ContactFields(otherMailAddress, firstnameB, surnameB)

    SMono(testee().index(accountId, otherContactFields)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 1)

    SMono(testee().update(accountId, updatedContact)).block()

    awaitDocumentsIndexed(MatchQuery("surname", "Carpenter"), 1)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Car")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(updatedContact)
  }

  @Test
  def updateDomainContactShouldSucceed(): Unit = {
    val updatedContact = ContactFields(mailAddressA, firstnameB, surnameB)

    SMono(testee().index(domain, contactFieldsA)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 1)

    SMono(testee().update(domain, updatedContact)).block()

    awaitDocumentsIndexed(MatchQuery("firstname", "Marie"), 1)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Mari")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(updatedContact)
  }

  @Test
  def updatePersonalContactShouldCreateContactIfNotExist(): Unit = {
    SMono(testee().update(accountId, contactFieldsA)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 1)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Car")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsA)
  }

  @Test
  def updateDomainContactShouldCreateContactIfNotExist(): Unit = {
    SMono(testee().update(domain, contactFieldsB)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 1)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Mari")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsB)
  }

  @Test
  def updatePersonalContactShouldOnlyUpdateTargetedContact(): Unit = {
    val updatedContact = ContactFields(otherMailAddress, firstnameB, surnameB)

    SMono(testee().index(accountId, contactFieldsA)).block()
    SMono(testee().index(accountId, otherContactFields)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    SMono(testee().update(accountId, updatedContact)).block()

    awaitDocumentsIndexed(MatchQuery("firstname", "Marie"), 1)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "John")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsA)
  }

  @Test
  def updateDomainContactShouldOnlyUpdateTargetedContact(): Unit = {
    val updatedContact = ContactFields(otherMailAddress, firstnameB, surnameB)

    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, otherContactFields)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    SMono(testee().update(domain, updatedContact)).block()

    awaitDocumentsIndexed(MatchQuery("firstname", "Marie"), 1)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "John")).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsA)
  }

  @Test
  def listAccountContactsShouldReturnEmptyWhenNone(): Unit = {
    assertThat(SFlux.fromPublisher(testee().list(accountId)).asJava().map(_.fields).collectList().block())
      .isEmpty()
  }

  @Test
  def listAccountContactsShouldReturnContacts(): Unit = {
    SMono(testee().index(accountId, contactFieldsA)).block()
    SMono(testee().index(accountId, contactFieldsB)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    assertThat(SFlux.fromPublisher(testee().list(accountId)).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsA, contactFieldsB)
  }

  @Test
  def listAccountContactsShouldNotReturnDomainContacts(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    assertThat(SFlux.fromPublisher(testee().list(accountId)).asJava().map(_.fields).collectList().block())
      .isEmpty()
  }

  @Test
  def listAccountContactsShouldBeAbleToReturnLargeNumberOfResults(): Unit = {
    IntStream.range(0, bigContactsNumber)
      .forEach((i: Int) => SMono(testee().index(accountId, ContactFields(new MailAddress(s"test$i@linagora.com")))).block())

    awaitDocumentsIndexed(MatchAllQuery(), bigContactsNumber)

    assertThat(SFlux.fromPublisher(testee().list(accountId)).asJava().collectList().block().size())
      .isEqualTo(bigContactsNumber)
  }

  @Test
  def listDomainContactsShouldReturnEmptyWhenNone(): Unit = {
    assertThat(SFlux.fromPublisher(testee().list(domain)).asJava().map(_.fields).collectList().block())
      .isEmpty()
  }

  @Test
  def listDomainContactsShouldReturnContacts(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    assertThat(SFlux.fromPublisher(testee().list(domain)).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsA, contactFieldsB)
  }

  @Test
  def listDomainContactsShouldNotReturnAccountContacts(): Unit = {
    SMono(testee().index(accountId, contactFieldsA)).block()
    SMono(testee().index(accountId, contactFieldsB)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    assertThat(SFlux.fromPublisher(testee().list(domain)).asJava().map(_.fields).collectList().block())
      .isEmpty()
  }

  @Test
  def listDomainContactsShouldBeAbleToReturnLargeNumberOfResults(): Unit = {
    IntStream.range(0, bigContactsNumber)
      .forEach((i: Int) => SMono(testee().index(domain, ContactFields(new MailAddress(s"test$i@linagora.com")))).block())

    awaitDocumentsIndexed(MatchAllQuery(), bigContactsNumber)

    assertThat(SFlux.fromPublisher(testee().list(domain)).asJava().collectList().block().size())
      .isEqualTo(bigContactsNumber)
  }

  @Test
  def listDomainsContactsShouldReturnEmptyWhenNone(): Unit = {
    assertThat(SFlux.fromPublisher(testee().listDomainsContacts()).asJava().map(_.fields).collectList().block())
      .isEmpty()
  }

  @Test
  def listDomainsContactsShouldReturnDomainContact(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 1)

    assertThat(SFlux.fromPublisher(testee().listDomainsContacts()).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsA)
  }

  @Test
  def listDomainsContactsShouldReturnContactsFromAllDomains(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()
    SMono(testee().index(Domain.of("other.com"), otherContactFields)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 3)

    assertThat(SFlux.fromPublisher(testee().listDomainsContacts()).asJava().map(_.fields).collectList().block())
      .containsExactlyInAnyOrder(contactFieldsA, contactFieldsB, otherContactFields)
  }

  @Test
  def listDomainsContactsShouldBeAbleToReturnLargeNumberOfResults(): Unit = {
    IntStream.range(0, bigContactsNumber)
      .forEach((i: Int) => SMono(testee().index(domain, ContactFields(new MailAddress(s"test$i@linagora.com")))).block())

    awaitDocumentsIndexed(MatchAllQuery(), bigContactsNumber)

    assertThat(SFlux.fromPublisher(testee().listDomainsContacts()).asJava().collectList().block().size())
      .isEqualTo(bigContactsNumber)
  }

  @Test
  def getAccountContactShouldReturnContact(): Unit = {
    SMono(testee().index(accountId, contactFieldsA)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 1)

    assertThat(SMono.fromPublisher(testee().get(accountId, mailAddressA)).asJava().map(_.fields).block())
      .isEqualTo(contactFieldsA)
  }

  @Test
  def getAccountContactShouldReturnNotFoundExceptionWhenDoesNotExist(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee().get(accountId, mailAddressA)).asJava().block())
      .isInstanceOf(classOf[ContactNotFoundException])
  }

  @Test
  def getAccountContactShouldReturnTheRightContact(): Unit = {
    SMono(testee().index(accountId, contactFieldsA)).block()
    SMono(testee().index(accountId, contactFieldsB)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    assertThat(SMono.fromPublisher(testee().get(accountId, mailAddressA)).asJava().map(_.fields).block())
      .isEqualTo(contactFieldsA)
  }

  @Test
  def getDomainContactShouldReturnContact(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 1)

    assertThat(SMono.fromPublisher(testee().get(domain, mailAddressA)).asJava().map(_.fields).block())
      .isEqualTo(contactFieldsA)
  }

  @Test
  def getDomainContactShouldReturnNotFoundExceptionWhenDoesNotExist(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee().get(domain, mailAddressA)).asJava().block())
      .isInstanceOf(classOf[ContactNotFoundException])
  }

  @Test
  def getDomainContactShouldReturnTheRightContact(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    assertThat(SMono.fromPublisher(testee().get(domain, mailAddressA)).asJava().map(_.fields).block())
      .isEqualTo(contactFieldsA)
  }
}
