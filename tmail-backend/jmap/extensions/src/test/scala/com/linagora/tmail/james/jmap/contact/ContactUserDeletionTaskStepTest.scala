package com.linagora.tmail.james.jmap.contact

import ContactUsernameChangeTaskStepTest.{ALICE, ALICE_ACCOUNT_ID, ALICE_CONTACT, ANDRE_CONTACT, BOB_ACCOUNT_ID, MARIE_CONTACT}
import org.apache.james.core.Domain
import org.assertj.core.api.Assertions.{assertThat, assertThatCode}
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class ContactUserDeletionTaskStepTest {
  var searchEngine: EmailAddressContactSearchEngine = _
  var testee: ContactUserDeletionTaskStep = _

  @BeforeEach
  def beforeEach(): Unit = {
    searchEngine = new InMemoryEmailAddressContactSearchEngine
    testee = new ContactUserDeletionTaskStep(searchEngine)
  }

  @Test
  def shouldRemoveContacts(): Unit = {
    SMono.fromPublisher(searchEngine.index(ALICE_ACCOUNT_ID, ANDRE_CONTACT)).block()
    SMono.fromPublisher(searchEngine.index(ALICE_ACCOUNT_ID, MARIE_CONTACT)).block()

    SMono.fromPublisher(testee.deleteUserData(ALICE)).block()

    assertThat(SFlux.fromPublisher(searchEngine.list(ALICE_ACCOUNT_ID))
      .map(_.fields)
      .collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def shouldNotRemoveContactsOfOtherUsers(): Unit = {
    SMono.fromPublisher(searchEngine.index(BOB_ACCOUNT_ID, ANDRE_CONTACT)).block()
    SMono.fromPublisher(searchEngine.index(BOB_ACCOUNT_ID, MARIE_CONTACT)).block()

    SMono.fromPublisher(testee.deleteUserData(ALICE)).block()

    assertThat(SFlux.fromPublisher(searchEngine.list(BOB_ACCOUNT_ID))
      .map(_.fields)
      .collectSeq().block().asJava)
      .containsExactlyInAnyOrder(ANDRE_CONTACT, MARIE_CONTACT)
  }

  @Test
  def shouldBeIdempotent(): Unit = {
    SMono.fromPublisher(testee.deleteUserData(ALICE)).block()

    assertThatCode(() => SMono.fromPublisher(testee.deleteUserData(ALICE)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def shouldRemoveTheContactInDomainContact(): Unit = {
    SMono.fromPublisher(searchEngine.index(Domain.of("linagora.com"), ALICE_CONTACT)).block()
    SMono.fromPublisher(searchEngine.index(Domain.of("linagora.com"), ANDRE_CONTACT)).block()

    SMono.fromPublisher(testee.deleteUserData(ALICE)).block()

    assertThat(SFlux.fromPublisher(searchEngine.list(Domain.of("linagora.com")))
      .map(_.fields)
      .collectSeq().block().asJava)
      .containsOnly(ANDRE_CONTACT)
  }

}
