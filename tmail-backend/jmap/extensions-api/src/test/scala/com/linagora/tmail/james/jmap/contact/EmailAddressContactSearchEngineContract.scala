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

import java.util.UUID
import java.util.stream.IntStream

import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngineContract.{accountId, accountIdB, bigContactsNumber, contactEmptyNameFieldsA, contactEmptyNameFieldsB, contactFieldsA, contactFieldsB, contactFieldsFrench, domain, firstnameB, mailAddressA, otherContactEmptyNameFields, otherContactFields, otherContactFieldsWithUppercaseEmail, otherMailAddress, surnameB}
import org.apache.james.core.{Domain, MailAddress, Username}
import org.apache.james.jmap.api.model.AccountId
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.{Nested, Test}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

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

  @ParameterizedTest
  @ValueSource(strings = Array("di", "dia", "dian", "diana"))
  def prefixSearchForFirstnameShouldWork(searchInput: String): Unit = {
    SMono(testee().index(accountId, ContactFields(new MailAddress("dpot@linagora.com"), firstname = "Diana", surname = "Pivot"))).block()

    awaitDocumentsIndexed(MatchAllQuery(), 1)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, searchInput)).asJava().map(_.fields.address).collectList().block())
      .containsExactlyInAnyOrder(new MailAddress("dpot@linagora.com"))
  }

  @ParameterizedTest
  @ValueSource(strings = Array("pi", "piv", "pivo", "pivot"))
  def prefixSearchForSurnameShouldWork(searchInput: String): Unit = {
    SMono(testee().index(accountId, ContactFields(new MailAddress("dpot@linagora.com"), firstname = "Diana", surname = "Pivot"))).block()

    awaitDocumentsIndexed(MatchAllQuery(), 1)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, searchInput)).asJava().map(_.fields.address).collectList().block())
      .containsExactlyInAnyOrder(new MailAddress("dpot@linagora.com"))
  }

  @ParameterizedTest
  @ValueSource(strings = Array("an", "and", "the", "then"))
  def prefixSearchWithStopWordShouldWork(searchInput: String): Unit = {
    SMono(testee().index(accountId, ContactFields(new MailAddress("dpot@linagora.com"), firstname = "Andre", surname = "Thena"))).block()

    awaitDocumentsIndexed(MatchAllQuery(), 1)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, searchInput)).asJava().map(_.fields.address).collectList().block())
      .containsExactlyInAnyOrder(new MailAddress("dpot@linagora.com"))
  }

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

  @ParameterizedTest
  @ValueSource(strings = Array("li", "lin", "lina", "linag", "linago", "linagor", "linagora"))
  def domainPrefixAutoCompleteShouldWorkForDomainContacts(searchInput: String): Unit = {
    val mailAddress: MailAddress = new MailAddress("nobita@linagora.com")
    val contactFields: ContactFields = ContactFields(mailAddress, "John", "Carpenter")

    // GIVEN that contact X is indexed in domain index
    SMono(testee().index(domain, contactFields)).block()
    awaitDocumentsIndexed(MatchAllQuery(), 1)

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, searchInput)).asJava().map(_.fields.address).collectList().block())
      .containsExactlyInAnyOrder(mailAddress)
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

  @Test
  def shouldReturnOnlyEmailMatchWhenPartHasAtSign(): Unit = {
    val mailAddress: MailAddress = new MailAddress("loic.sineau@example.com")
    val contactFields: ContactFields = ContactFields(mailAddress, "SINEAU", "Loic")
    SMono(testee().index(accountId, contactFields)).block()
    SMono(testee().index(domain, contactFields)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 2)

    val partHasAtSign: String = "anyone@loic"

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, partHasAtSign))
      .map(_.fields.address)
      .collectSeq()
      .block().asJava)
      .doesNotContain(mailAddress)
  }

  @Test
  def shouldReturnOnlyPrefixedByUserInput(): Unit = {
    val mailAddress: MailAddress = new MailAddress("bob@linagora.com")
    val contactFields: ContactFields = ContactFields(mailAddress, "Bin", "Zet")
    SMono(testee().index(accountId, contactFields)).block()
    SMono(testee().index(domain, contactFields)).block()

    val whatEverMailAddress: MailAddress = new MailAddress("whatever@linagora.com")
    val whatEverContactFields: ContactFields = ContactFields(whatEverMailAddress, "What", "Ever")

    SMono(testee().index(accountId, whatEverContactFields)).block()
    SMono(testee().index(domain, whatEverContactFields)).block()

    awaitDocumentsIndexed(MatchAllQuery(), 4)

    val prefixedPath: String = "bob@linagora.c"

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, prefixedPath))
      .map(_.fields.address)
      .collectSeq()
      .block().asJava)
      .containsExactlyInAnyOrder(mailAddress)
      .doesNotContain(whatEverMailAddress)
  }

  @Nested
  class AddressBookId {
    @Test
    def indexWithAddressBookIdShouldIndex(): Unit = {
      val addressBookId = UUID.randomUUID().toString
      val contact = ContactFields(new MailAddress("dpot@linagora.com"), firstname = "Diana", surname = "Pivot")

      SMono(testee().index(accountId, contact, addressBookId)).block()
      awaitDocumentsIndexed(MatchAllQuery(), 1)

      assertThat(SFlux(testee().autoComplete(accountId, "diana"))
        .map(_.fields.address).asJavaList)
        .containsExactly(new MailAddress("dpot@linagora.com"))
    }

    @Test
    def indexWithSameAddressBookIdShouldIndex(): Unit = {
      val addressBookId = UUID.randomUUID().toString
      val contact1 = ContactFields(new MailAddress("iphone13@linagora.com"), firstname = "Iphone 13", surname = "Pivot")
      val contact2 = ContactFields(new MailAddress("ihpone14@linagora.com"), firstname = "Iphone 14", surname = "Pivot")

      SMono(testee().index(accountId, contact1, addressBookId)).block()
      SMono(testee().index(accountId, contact2, addressBookId)).block()
      awaitDocumentsIndexed(MatchAllQuery(), 2)

      assertThat(SFlux(testee().autoComplete(accountId, "iphone"))
        .map(_.fields.address).asJavaList).hasSize(2)
    }

    @Test
    def updateWithAddressBookIdShouldUpdate(): Unit = {
      val addressBookId = UUID.randomUUID().toString
      val initial = ContactFields(new MailAddress("nobi@linagora.com"), firstname = "Nobi", surname = "Kun")
      val updated = ContactFields(new MailAddress("nobi@linagora.com"), firstname = "Shin", surname = "Kun")

      SMono(testee().index(accountId, initial, addressBookId)).block()
      awaitDocumentsIndexed(MatchAllQuery(), 1)

      SMono(testee().update(accountId, updated, addressBookId)).block()
      awaitDocumentsIndexed(MatchQuery("firstname", "Shin"), 1)

      assertThat(SFlux(testee().autoComplete(accountId, "shin"))
        .map(_.fields.firstname).asJavaList).containsExactlyInAnyOrder("Shin")
    }

    @Test
    def deleteWithAddressBookIdShouldRemoveContact(): Unit = {
      val addressBookId = UUID.randomUUID().toString
      val mail = new MailAddress("gone@linagora.com")
      val fields = ContactFields(mail, firstname = "Gone", surname = "Person")

      SMono(testee().index(accountId, fields, addressBookId)).block()
      awaitDocumentsIndexed(MatchAllQuery(), 1)

      SMono(testee().delete(accountId, mail, addressBookId)).block()
      awaitDocumentsIndexed(MatchAllQuery(), 0)

      assertThat(SFlux(testee().autoComplete(accountId, "gone")).asJavaList).isEmpty()
    }

    @Test
    def deleteWithoutAddressBookIdShouldDeleteVariants(): Unit = {
      val mailAddress = new MailAddress("multi@linagora.com")
      val mailAddressOther = new MailAddress("other@linagora.com")
      val addressBookId1 = UUID.randomUUID().toString
      val addressBookId2 = UUID.randomUUID().toString

      SMono(testee().index(accountId,  ContactFields(mailAddress, firstname = "multi", surname = "Contact One"))).block()
      SMono(testee().index(accountId,  ContactFields(mailAddress, firstname = "multi", surname = "Contact Two"), addressBookId1)).block()
      SMono(testee().index(accountId,  ContactFields(mailAddressOther, firstname = "Other", surname = "Contact"), addressBookId2)).block()

      awaitDocumentsIndexed(MatchAllQuery(), 3)
      SMono(testee().delete(accountId, mailAddress)).block()
      awaitDocumentsIndexed(MatchAllQuery(), 1)

      assertThat(SFlux(testee().autoComplete(accountId, "multi")).asJavaList).isEmpty()
      // should not delete other contact
      assertThat(SFlux(testee().autoComplete(accountId, "other")).asJavaList).hasSize(1)
    }

    @Test
    def shouldReturnAllVariantContact(): Unit = {
      val mailAddress: MailAddress = new MailAddress("nobita@linagora.com")
      val contactFields1: ContactFields = ContactFields(mailAddress, "John", "Carpenter")
      val contactFields2: ContactFields = ContactFields(mailAddress, "Boss", "John")
      val addressBookId1 = UUID.randomUUID().toString
      val addressBookId2 = UUID.randomUUID().toString

      SMono(testee().index(accountId, contactFields1, addressBookId1)).block()
      SMono(testee().index(accountId, contactFields2, addressBookId2)).block()

      awaitDocumentsIndexed(MatchAllQuery(), 2)
      assertThat(SFlux(testee().autoComplete(accountId, mailAddress.asString())).map(_.fields).asJava().collectList().block())
        .containsExactlyInAnyOrder(contactFields1, contactFields2)
    }

    @Test
    def autoCompleteShouldReturnAllMatchesWhenIndexingWithDifferentAddressBookIds(): Unit = {
      val addressBookIdA = UUID.randomUUID().toString
      val addressBookIdB = UUID.randomUUID().toString
      SMono(testee().index(accountId, contactEmptyNameFieldsA, addressBookIdA)).block()
      SMono(testee().index(accountId, contactEmptyNameFieldsB, addressBookIdB)).block()

      awaitDocumentsIndexed(MatchAllQuery(), 2)

      assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "bit")).asJava().map(_.fields).collectList().block())
        .containsExactlyInAnyOrder(contactEmptyNameFieldsA, contactEmptyNameFieldsB)
    }

    @Test
    def listShouldReturnAllContactsForAccountId(): Unit = {
      val mail1 = new MailAddress("first@linagora.com")
      val mail2 = new MailAddress("second@linagora.com")
      val addressBookId1 = UUID.randomUUID().toString
      val addressBookId2 = UUID.randomUUID().toString

      SMono(testee().index(accountId, ContactFields(mail1, "First", "Contact"))).block()
      SMono(testee().index(accountId, ContactFields(mail2, "Second", "Contact-1"), addressBookId1)).block()
      SMono(testee().index(accountId, ContactFields(mail2, "Second", "Contact-2"), addressBookId2)).block()
      awaitDocumentsIndexed(MatchAllQuery(), 3)

      assertThat(SFlux(testee().list(accountId))
        .map(_.fields.address)
        .asJavaList).containsExactlyInAnyOrder(mail1, mail2, mail2)
    }

    @Test
    def listByAddressBookIdShouldReturnContactWithExactAddressBookId(): Unit = {
      val mailAddress = new MailAddress("nobita@linagora.com")
      val contactFields = ContactFields(mailAddress, "Nobita", "Nobi")
      val addressBookId = UUID.randomUUID().toString

      SMono(testee().index(accountId, contactFields, addressBookId)).block()
      awaitDocumentsIndexed(MatchAllQuery(), 1)

      assertThat(SFlux(testee().list(accountId, addressBookId)).map(_.fields).asJavaList).containsExactly(contactFields)
    }

    @Test
    def listByAddressBookIdShouldReturnEmptyWhenWrongAddressBookId(): Unit = {
      val mailAddress = new MailAddress("nobita@linagora.com")
      val contactFields = ContactFields(mailAddress, "Nobita", "Nobi")
      val addressBookId = UUID.randomUUID().toString
      val wrongContactId = UUID.randomUUID().toString

      SMono(testee().index(accountId, contactFields, addressBookId)).block()
      awaitDocumentsIndexed(MatchAllQuery(), 1)
      assertThat(SFlux(testee().list(accountId, wrongContactId)).asJavaList).isEmpty()
    }

    @Test
    def listByAddressBookIdShouldReturnEmptyWhenAccountIdDoesNotMatch(): Unit = {
      val mailAddress = new MailAddress("nobita@linagora.com")
      val contactFields = ContactFields(mailAddress, "Nobita", "Nobi")
      val addressBookId = UUID.randomUUID().toString
      val otherAccount = AccountId.fromString("other@linagora.com")

      SMono(testee().index(accountId, contactFields, addressBookId)).block()
      awaitDocumentsIndexed(MatchAllQuery(), 1)

      assertThat(SFlux(testee().list(otherAccount, addressBookId)).asJavaList).isEmpty()
    }

    @Test
    def listByAddressBookIdShouldReturnOnlyMatchingContactWhenMultipleExist(): Unit = {
      val contactFields1 = ContactFields(new MailAddress("nobita@linagora.com"), "Nobita", "One")
      val contactFields2 = ContactFields(new MailAddress("sakura@linagora.com"), "Sakura", "Two")
      val contactFields3 = ContactFields(new MailAddress("sasuke@linagora.com"), "Sasuke", "Three")
      val id1 = UUID.randomUUID().toString
      val id2 = UUID.randomUUID().toString

      SMono(testee().index(accountId, contactFields1, id1)).block()
      SMono(testee().index(accountId, contactFields2, id2)).block()
      SMono(testee().index(accountId, contactFields3, id2)).block()
      awaitDocumentsIndexed(MatchAllQuery(), 3)

      assertThat(SFlux(testee().list(accountId, id2)).map(_.fields).asJavaList)
        .containsExactlyInAnyOrder(contactFields2, contactFields3)
        .doesNotContain(contactFields1)
    }

    @Test
    def deleteShouldRemoveContactWhenAddressAndIdMatch(): Unit = {
      val mailAddress = new MailAddress("nobita@linagora.com")
      val contactFields1 = ContactFields(mailAddress, "Nobita", "One")
      val contactFields2 = ContactFields(mailAddress, "Nobita", "Two")
      val addressBookId1 = UUID.randomUUID().toString
      val addressBookId2 = UUID.randomUUID().toString

      SMono(testee().index(accountId, contactFields1, addressBookId1)).block()
      SMono(testee().index(accountId, contactFields2, addressBookId2)).block()
      awaitDocumentsIndexed(MatchAllQuery(), 2)

      SMono(testee().delete(accountId, mailAddress, addressBookId2)).block()
      awaitDocumentsIndexed(MatchAllQuery(), 1)
      assertThat(SFlux(testee().list(accountId, addressBookId2)).asJavaList).isEmpty()
      assertThat(SFlux(testee().list(accountId, addressBookId1)).asJavaList).hasSize(1)
    }

    @Test
    def deleteShouldNotRemoveWhenAddressDoesNotMatch(): Unit = {
      val mailAddress = new MailAddress("nobita@linagora.com")
      val contactFields1 = ContactFields(mailAddress, "Nobita", "One")
      val addressBookId = UUID.randomUUID().toString

      SMono(testee().index(accountId, contactFields1, addressBookId)).block()
      awaitDocumentsIndexed(MatchAllQuery(), 1)

      SMono(testee().delete(accountId,  new MailAddress("sasuke@linagora.com"), addressBookId)).block()
      assertThat(SFlux(testee().list(accountId, addressBookId)).asJavaList).hasSize(1)
    }

    @Test
    def deleteShouldNotThrowWhenNoContactExistsForAccount(): Unit = {
      assertThatCode(() => SMono(testee().delete(accountId,  new MailAddress(UUID.randomUUID().toString + "@linagora.com"), UUID.randomUUID().toString)).block())
        .doesNotThrowAnyException()
    }

    @Test
    def deleteShouldNotRemoveContactWhenAddressBookIdDoesNotMatch(): Unit = {
      val mailAddress = new MailAddress("nobita@linagora.com")
      val addressBookId = UUID.randomUUID().toString
      val contact1 = ContactFields(mailAddress, "Nobita", "One")
      val contact2 = ContactFields(mailAddress, "Sasuke", "Two")

      SMono(testee().index(accountId, contact1, addressBookId)).block()
      SMono(testee().index(accountId, contact2)).block()
      awaitDocumentsIndexed(MatchAllQuery(), 2)

      SMono(testee().delete(accountId, mailAddress, UUID.randomUUID().toString)).block()
      assertThat(SFlux(testee().list(accountId)).map(_.fields).asJavaList).containsExactlyInAnyOrder(contact1, contact2)
    }
  }

  implicit class ImplicitPublisher[U](publisher: Publisher[U]) {
    def asJavaList: java.util.List[U] =
      SFlux(publisher).collectSeq().block().asJava
  }

}
