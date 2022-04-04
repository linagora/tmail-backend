package com.linagora.tmail.james.jmap.contact

import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngineContract.{accountId, accountIdB, contactEmptyNameFieldsA, contactEmptyNameFieldsB, contactFieldsA, contactFieldsB, domain, otherContactEmptyNameFields, otherContactFields, otherContactFieldsWithUppercaseEmail}
import org.apache.james.core.{Domain, MailAddress, Username}
import org.apache.james.jmap.api.model.AccountId
import org.assertj.core.api.Assertions.assertThat
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
}

trait EmailAddressContactSearchEngineContract {
  def testee(): EmailAddressContactSearchEngine

  def awaitDocumentsIndexed(documentCount: Long): Unit

  @Test
  def indexShouldReturnMatched(): Unit = {
    SMono(testee().index(accountId, contactEmptyNameFieldsA)).block()
    SMono(testee().index(accountId, contactEmptyNameFieldsB)).block()

    awaitDocumentsIndexed(2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "bit")).asJava().map(_.fields).collectList().block())
      .containsOnly(contactEmptyNameFieldsA, contactEmptyNameFieldsB)
  }

  @Test
  def indexShouldReturnNoMatch(): Unit = {
    SMono(testee().index(accountId, contactEmptyNameFieldsA)).block()
    SMono(testee().index(accountId, contactEmptyNameFieldsB)).block()

    awaitDocumentsIndexed(2)

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

    awaitDocumentsIndexed(1)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountIdB, "bit")).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def indexDomainShouldReturnMatched(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()

    awaitDocumentsIndexed(2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "bit")).asJava().map(_.fields).collectList().block())
      .containsOnly(contactFieldsA, contactFieldsB)
  }

  @Test
  def indexDomainShouldReturnNoMatch(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()

    awaitDocumentsIndexed(2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "dora")).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def indexShouldMatchDomainAndPersonalContacts(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()
    SMono(testee().index(accountId, otherContactEmptyNameFields)).block()

    awaitDocumentsIndexed(3)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "bit")).asJava().map(_.fields).collectList().block())
      .containsOnly(contactFieldsA, contactFieldsB, otherContactEmptyNameFields)
  }

  @Test
  def indexShouldReturnFirstnameMatched(): Unit = {
    SMono(testee().index(accountId, contactFieldsA)).block()
    SMono(testee().index(accountId, contactFieldsB)).block()

    awaitDocumentsIndexed(2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Mari")).asJava().map(_.fields).collectList().block())
      .containsOnly(contactFieldsB)
  }

  @Test
  def indexShouldReturnSurnameMatched(): Unit = {
    SMono(testee().index(accountId, contactFieldsA)).block()
    SMono(testee().index(accountId, contactFieldsB)).block()

    awaitDocumentsIndexed(2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Carpent")).asJava().map(_.fields).collectList().block())
      .containsOnly(contactFieldsA, contactFieldsB)
  }

  @Test
  def indexShouldReturnNoNameMatch(): Unit = {
    SMono(testee().index(accountId, contactFieldsA)).block()
    SMono(testee().index(accountId, contactFieldsB)).block()

    awaitDocumentsIndexed(2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "dora")).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def indexDomainShouldReturnFirstnameMatched(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()

    awaitDocumentsIndexed(2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Mari")).asJava().map(_.fields).collectList().block())
      .containsOnly(contactFieldsB)
  }

  @Test
  def indexDomainShouldReturnSurnameMatched(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()

    awaitDocumentsIndexed(2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Carpent")).asJava().map(_.fields).collectList().block())
      .containsOnly(contactFieldsA, contactFieldsB)
  }

  @Test
  def indexDomainShouldReturnNoNameMatch(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()

    awaitDocumentsIndexed(2)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "dora")).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def indexShouldMatchFirstnameDomainAndPersonalContacts(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()
    SMono(testee().index(accountId, otherContactFields)).block()

    awaitDocumentsIndexed(3)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "John")).asJava().map(_.fields).collectList().block())
      .containsOnly(contactFieldsA, otherContactFields)
  }

  @Test
  def indexShouldMatchLastnameDomainAndPersonalContacts(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()
    SMono(testee().index(accountId, otherContactFields)).block()

    awaitDocumentsIndexed(3)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Car")).asJava().map(_.fields).collectList().block())
      .containsOnly(contactFieldsA, contactFieldsB, otherContactFields)
  }

  @Test
  def indexShouldMixMatchDomainAndPersonalContacts(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()
    SMono(testee().index(accountId, otherContactFields)).block()

    awaitDocumentsIndexed(3)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "Car")).asJava().map(_.fields).collectList().block())
      .containsOnly(contactFieldsA, contactFieldsB, otherContactFields)
  }

  @Test
  def firstnameAndSurnameAutoCompleteShouldSupportBothLowerCaseAndUpperCase(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(domain, contactFieldsB)).block()
    SMono(testee().index(accountId, otherContactFields)).block()

    awaitDocumentsIndexed(3)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "CAR")).asJava().map(_.fields).collectList().block())
        .containsOnly(contactFieldsA, contactFieldsB, otherContactFields)
      softly.assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "car")).asJava().map(_.fields).collectList().block())
        .containsOnly(contactFieldsA, contactFieldsB, otherContactFields)
    })
  }

  @Test
  def emailAutoCompleteShouldSupportBothLowerCaseAndUpperCase(): Unit = {
    SMono(testee().index(domain, contactFieldsA)).block()
    SMono(testee().index(accountId, otherContactFields)).block()
    SMono(testee().index(accountId, otherContactFieldsWithUppercaseEmail)).block()

    awaitDocumentsIndexed(3)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "nobita@linagora.com")).asJava().map(_.fields).collectList().block())
        .containsOnly(contactFieldsA)
      softly.assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "NOBITA@LINAGORA.COM")).asJava().map(_.fields).collectList().block())
        .containsOnly(contactFieldsA)

      softly.assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "nobitu@other.com")).asJava().map(_.fields).collectList().block())
        .containsOnly(otherContactFields)
      softly.assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "NOBITU@OTHER.COM")).asJava().map(_.fields).collectList().block())
        .containsOnly(otherContactFields)

      softly.assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "JOHNDOE@OTHER.COM")).asJava().map(_.fields).collectList().block())
        .containsOnly(otherContactFieldsWithUppercaseEmail)
      softly.assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "johndoe@other.com")).asJava().map(_.fields).collectList().block())
        .containsOnly(otherContactFieldsWithUppercaseEmail)
    })
  }
}
