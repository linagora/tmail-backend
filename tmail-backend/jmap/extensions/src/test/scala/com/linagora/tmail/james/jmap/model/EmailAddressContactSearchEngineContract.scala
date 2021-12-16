package com.linagora.tmail.james.jmap.model

import org.junit.jupiter.api.Test
import com.linagora.tmail.james.jmap.model.EmailAddressContactSearchEngineContract.{accountId, accountIdB}
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.AccountId
import org.assertj.core.api.Assertions.assertThat
import reactor.core.scala.publisher.{SFlux, SMono}

import java.util.UUID

object EmailAddressContactSearchEngineContract {
  private val accountId: AccountId = AccountId.fromUsername(Username.of("bob@lingora.com"))
  private val accountIdB: AccountId = AccountId.fromUsername(Username.of("doraemon@linagora.com"))
}

trait EmailAddressContactSearchEngineContract {
  def testee: EmailAddressContactSearchEngine

  @Test
  def indexShouldReturnMatched(): Unit = {
    val contact1 = EmailAddressContact(UUID.randomUUID(), "nobita@linagora.com")
    val contact2 = EmailAddressContact(UUID.randomUUID(), "nobito@linagora.com")
    SMono(testee.index(accountId, contact1)).block()
    SMono(testee.index(accountId, contact2)).block()

    assertThat(SFlux.fromPublisher(testee.autoComplete(accountId, "bit")).asJava().collectList().block())
      .containsOnly(contact1, contact2)
  }

  @Test
  def indexShouldReturnNoMatch(): Unit = {
    SMono(testee.index(accountId, EmailAddressContact(UUID.randomUUID(), "nobita@linagora.com"))).block()
    SMono(testee.index(accountId, EmailAddressContact(UUID.randomUUID(), "nobito@linagora.com"))).block()

    assertThat(SFlux.fromPublisher(testee.autoComplete(accountId, "dora")).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def indexShouldReturnEmpty(): Unit = {
    assertThat(SFlux.fromPublisher(testee.autoComplete(accountId, "any")).asJava().collectList().block())
      .isEmpty()
  }

  @Test
  def indexWithDifferentAccount: Unit = {
    SMono(testee.index(accountId, EmailAddressContact(UUID.randomUUID(), "nobita@linagora.com"))).block()

    assertThat(SFlux.fromPublisher(testee.autoComplete(accountIdB, "bit")).asJava().collectList().block())
      .isEmpty()
  }
}
