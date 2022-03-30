package com.linagora.tmail.james.jmap.contact

import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngineContract.{accountId, accountIdB, domain, firstnameA, firstnameB, mailAddressA, mailAddressB, otherFirstname, otherLastName, otherMailAddress, surnameA, surnameB}
import org.apache.james.core.{Domain, MailAddress, Username}
import org.apache.james.jmap.api.model.AccountId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.{SFlux, SMono}

object EmailAddressContactSearchEngineContract {
  private val domain: Domain = Domain.of("linagora.com")

  private val accountId: AccountId = AccountId.fromUsername(Username.fromLocalPartWithDomain("bob", domain))
  private val accountIdB: AccountId = AccountId.fromUsername(Username.fromLocalPartWithDomain("doraemon", domain))

  private val mailAddressA: MailAddress = new MailAddress("nobita@linagora.com")
  private val mailAddressB: MailAddress = new MailAddress("nobito@linagora.com")

  private val firstnameA: String = "John"
  private val surnameA: String = "Carpenter"

  private val firstnameB: String = "Marie"
  private val surnameB: String = "Carpenter"

  private val otherMailAddress: MailAddress = new MailAddress("nobitu@other.com")
  private val otherFirstname: String = "Johnny"
  private val otherLastName: String = "Ariepent"
}

trait EmailAddressContactSearchEngineContract {
  def testee(): EmailAddressContactSearchEngine

  @Test
  def indexShouldReturnMatched(): Unit = {
    SMono(testee().index(accountId, mailAddressA)).block()
    SMono(testee().index(accountId, mailAddressB)).block()

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "bit")).asJava().map(_.address).collectList().block())
      .containsOnly(mailAddressA, mailAddressB)
  }

  @Test
  def indexShouldReturnNoMatch(): Unit = {
    SMono(testee().index(accountId, mailAddressA)).block()
    SMono(testee().index(accountId, mailAddressB)).block()

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
    SMono(testee().index(accountId, mailAddressA)).block()

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountIdB, "bit")).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def indexDomainShouldReturnMatched(): Unit = {
    SMono(testee().index(domain, mailAddressA, firstnameA, surnameA)).block()
    SMono(testee().index(domain, mailAddressB, firstnameB, surnameB)).block()

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "bit")).asJava().map(_.address).collectList().block())
      .containsOnly(mailAddressA, mailAddressB)
  }

  @Test
  def indexDomainShouldReturnNoMatch(): Unit = {
    SMono(testee().index(domain, mailAddressA, firstnameA, surnameA)).block()
    SMono(testee().index(domain, mailAddressB, firstnameB, surnameB)).block()

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "dora")).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def indexShouldMatchDomainAndPersonalContacts(): Unit = {
    SMono(testee().index(domain, mailAddressA, firstnameA, surnameA)).block()
    SMono(testee().index(domain, mailAddressB, firstnameB, surnameB)).block()
    SMono(testee().index(accountId, otherMailAddress)).block()

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "bit")).asJava().map(_.address).collectList().block())
      .containsOnly(mailAddressA, mailAddressB, otherMailAddress)
  }

  @Test
  def indexShouldReturnFirstnameMatched(): Unit = {
    SMono(testee().index(accountId, mailAddressA, firstnameA, surnameA)).block()
    SMono(testee().index(accountId, mailAddressB, firstnameB, surnameB)).block()

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "ari")).asJava().map(_.address).collectList().block())
      .containsOnly(mailAddressB)
  }

  @Test
  def indexShouldReturnSurnameMatched(): Unit = {
    SMono(testee().index(accountId, mailAddressA, firstnameA, surnameA)).block()
    SMono(testee().index(accountId, mailAddressB, firstnameB, surnameB)).block()

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "pen")).asJava().map(_.address).collectList().block())
      .containsOnly(mailAddressA, mailAddressB)
  }

  @Test
  def indexShouldReturnNoNameMatch(): Unit = {
    SMono(testee().index(accountId, mailAddressA, firstnameA, surnameA)).block()
    SMono(testee().index(accountId, mailAddressB, firstnameB, surnameB)).block()

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "dora")).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def indexDomainShouldReturnFirstnameMatched(): Unit = {
    SMono(testee().index(domain, mailAddressA, firstnameA, surnameA)).block()
    SMono(testee().index(domain, mailAddressB, firstnameB, surnameB)).block()

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "ari")).asJava().map(_.address).collectList().block())
      .containsOnly(mailAddressB)
  }

  @Test
  def indexDomainShouldReturnSurnameMatched(): Unit = {
    SMono(testee().index(domain, mailAddressA, firstnameA, surnameA)).block()
    SMono(testee().index(domain, mailAddressB, firstnameB, surnameB)).block()

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "pen")).asJava().map(_.address).collectList().block())
      .containsOnly(mailAddressA, mailAddressB)
  }

  @Test
  def indexDomainShouldReturnNoNameMatch(): Unit = {
    SMono(testee().index(domain, mailAddressA, firstnameA, surnameA)).block()
    SMono(testee().index(domain, mailAddressB, firstnameB, surnameB)).block()

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "dora")).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def indexShouldMatchFirstnameDomainAndPersonalContacts(): Unit = {
    SMono(testee().index(domain, mailAddressA, firstnameA, surnameA)).block()
    SMono(testee().index(domain, mailAddressB, firstnameB, surnameB)).block()
    SMono(testee().index(accountId, otherMailAddress, otherFirstname, otherLastName)).block()

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "ohn")).asJava().map(_.address).collectList().block())
      .containsOnly(mailAddressA, otherMailAddress)
  }

  @Test
  def indexShouldMatchLastnameDomainAndPersonalContacts(): Unit = {
    SMono(testee().index(domain, mailAddressA, firstnameA, surnameA)).block()
    SMono(testee().index(domain, mailAddressB, firstnameB, surnameB)).block()
    SMono(testee().index(accountId, otherMailAddress, otherFirstname, otherLastName)).block()

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "pen")).asJava().map(_.address).collectList().block())
      .containsOnly(mailAddressA, mailAddressB, otherMailAddress)
  }

  @Test
  def indexShouldMixMatchDomainAndPersonalContacts(): Unit = {
    SMono(testee().index(domain, mailAddressA, firstnameA, surnameA)).block()
    SMono(testee().index(domain, mailAddressB, firstnameB, surnameB)).block()
    SMono(testee().index(accountId, otherMailAddress, otherFirstname, otherLastName)).block()

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "rie")).asJava().map(_.address).collectList().block())
      .containsOnly(mailAddressB, otherMailAddress)
  }
}
