package com.linagora.tmail.james.jmap.contact

import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngineContract.{accountId, accountIdB, domain, mailAddressA, mailAddressB, otherMailAddress}
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

  private val otherMailAddress: MailAddress = new MailAddress("nobitu@other.com")

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
    SMono(testee().index(domain, mailAddressA)).block()
    SMono(testee().index(domain, mailAddressB)).block()

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "bit")).asJava().map(_.address).collectList().block())
      .containsOnly(mailAddressA, mailAddressB)
  }

  @Test
  def indexDomainShouldReturnNoMatch(): Unit = {
    SMono(testee().index(domain, mailAddressA)).block()
    SMono(testee().index(domain, mailAddressB)).block()

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "dora")).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def indexShouldMatchDomainAndPersonalContacts(): Unit = {
    SMono(testee().index(domain, mailAddressA)).block()
    SMono(testee().index(domain, mailAddressB)).block()
    SMono(testee().index(accountId, otherMailAddress)).block()

    assertThat(SFlux.fromPublisher(testee().autoComplete(accountId, "bit")).asJava().map(_.address).collectList().block())
      .containsOnly(mailAddressA, mailAddressB, otherMailAddress)
  }
}
