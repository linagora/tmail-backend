package com.linagora.tmail.james.jmap.contact

import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngineContract.{accountId, accountIdB, mailAddressA, mailAddressB}
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.api.model.AccountId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.{SFlux, SMono}

object EmailAddressContactSearchEngineContract {
  private val accountId: AccountId = AccountId.fromUsername(Username.of("bob@lingora.com"))
  private val accountIdB: AccountId = AccountId.fromUsername(Username.of("doraemon@linagora.com"))

  private val mailAddressA: MailAddress = new MailAddress("nobita@linagora.com")
  private val mailAddressB: MailAddress = new MailAddress("nobito@linagora.com")

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
}
