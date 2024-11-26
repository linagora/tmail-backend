package com.linagora.tmail.james.jmap.contact

import ContactUsernameChangeTaskStepTest.{ALICE, ALICE_ACCOUNT_ID, ANDRE_CONTACT, BOB, BOB_ACCOUNT_ID, MARIE_CONTACT}
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.api.model.AccountId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

object ContactUsernameChangeTaskStepTest {
  val ALICE: Username = Username.of("alice@linagora.com")
  val BOB: Username = Username.of("bob@linagora.com")
  val ALICE_ACCOUNT_ID: AccountId = AccountId.fromUsername(ALICE)
  val BOB_ACCOUNT_ID: AccountId = AccountId.fromUsername(BOB)

  val ANDRE_MAIL_ADDRESS: MailAddress = new MailAddress("andre@linagora.com")
  val ANDRE_CONTACT: ContactFields = ContactFields(ANDRE_MAIL_ADDRESS, "Andre", "Dupont")
  val MARIE_MAIL_ADDRESS: MailAddress = new MailAddress("marie@linagora.com")
  val MARIE_CONTACT: ContactFields = ContactFields(MARIE_MAIL_ADDRESS, "Marie", "Bourdier")
  val ALICE_MAIL_ADDRESS: MailAddress = new MailAddress("alice@linagora.com")
  val ALICE_CONTACT: ContactFields = ContactFields(ALICE_MAIL_ADDRESS, "Alice", "Gwen")
}

class ContactUsernameChangeTaskStepTest {
  var searchEngine: EmailAddressContactSearchEngine = _
  var testee: ContactUsernameChangeTaskStep = _

  @BeforeEach
  def beforeEach(): Unit = {
    searchEngine = new InMemoryEmailAddressContactSearchEngine
    testee = new ContactUsernameChangeTaskStep(searchEngine)
  }

  @Test
  def shouldMigrateContacts(): Unit = {
    SMono.fromPublisher(searchEngine.index(ALICE_ACCOUNT_ID, ANDRE_CONTACT)).block()

    SMono.fromPublisher(testee.changeUsername(ALICE, BOB)).block()

    assertThat(SFlux.fromPublisher(searchEngine.list(BOB_ACCOUNT_ID))
      .map(_.fields)
      .collectSeq().block().asJava)
      .containsExactlyInAnyOrder(ANDRE_CONTACT)
  }

  @Test
  def shouldRemoveContactsFromOriginalAccount(): Unit = {
    SMono.fromPublisher(searchEngine.index(ALICE_ACCOUNT_ID, ANDRE_CONTACT)).block()

    SMono.fromPublisher(testee.changeUsername(ALICE, BOB)).block()

    assertThat(SFlux.fromPublisher(searchEngine.list(ALICE_ACCOUNT_ID))
      .map(_.fields)
      .collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def shouldMigrateMultipleContacts(): Unit = {
    SMono.fromPublisher(searchEngine.index(ALICE_ACCOUNT_ID, ANDRE_CONTACT)).block()
    SMono.fromPublisher(searchEngine.index(ALICE_ACCOUNT_ID, MARIE_CONTACT)).block()

    SMono.fromPublisher(testee.changeUsername(ALICE, BOB)).block()

    assertThat(SFlux.fromPublisher(searchEngine.list(BOB_ACCOUNT_ID))
      .map(_.fields)
      .collectSeq().block().asJava)
      .containsExactlyInAnyOrder(ANDRE_CONTACT, MARIE_CONTACT)
  }

  @Test
  def shouldNotOverrideContactsFromDestinationAccount(): Unit = {
    SMono.fromPublisher(searchEngine.index(ALICE_ACCOUNT_ID, ANDRE_CONTACT)).block()
    SMono.fromPublisher(searchEngine.index(BOB_ACCOUNT_ID, MARIE_CONTACT)).block()

    SMono.fromPublisher(testee.changeUsername(ALICE, BOB)).block()

    assertThat(SFlux.fromPublisher(searchEngine.list(BOB_ACCOUNT_ID))
      .map(_.fields)
      .collectSeq().block().asJava)
      .containsExactlyInAnyOrder(ANDRE_CONTACT, MARIE_CONTACT)
  }
}
