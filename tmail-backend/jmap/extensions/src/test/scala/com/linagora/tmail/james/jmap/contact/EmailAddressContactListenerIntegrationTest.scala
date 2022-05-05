package com.linagora.tmail.james.jmap.contact

import com.linagora.tmail.james.jmap.contact.EmailAddressContactListenerIntegrationTest.{ACCOUNT_ID, CONTACT, CONTACT_ADDED_EVENT}
import org.apache.james.core.MailAddress
import org.apache.james.events.EventBusTestFixture.{EVENT_ID, NO_KEYS, USERNAME}
import org.apache.james.events.delivery.InVmEventDelivery
import org.apache.james.events.{EventBus, InVMEventBus, MemoryEventDeadLetters, RetryBackoffConfiguration}
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.metrics.tests.RecordingMetricFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.SFlux

import scala.jdk.CollectionConverters._

object EmailAddressContactListenerIntegrationTest {
  val ACCOUNT_ID: AccountId = AccountId.fromUsername(USERNAME)
  val MAIL_ADDRESS: MailAddress = new MailAddress("contact1@linagora.com")
  val CONTACT: ContactFields = ContactFields(MAIL_ADDRESS, "FirstName1", "Last Name 2")
  val CONTACT_ADDED_EVENT: TmailContactUserAddedEvent = TmailContactUserAddedEvent(
    eventId = EVENT_ID,
    username = USERNAME,
    contact = CONTACT)
}

class EmailAddressContactListenerIntegrationTest {

  var eventBus: EventBus = _
  var searchEngine: EmailAddressContactSearchEngine = _

  @BeforeEach
  def beforeEach(): Unit = {
    searchEngine = new InMemoryEmailAddressContactSearchEngine
    eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory), RetryBackoffConfiguration.DEFAULT, new MemoryEventDeadLetters())
    eventBus.register(new EmailAddressContactListener(searchEngine))
  }

  @Test
  def shouldIndexContactWhenHasContactUserAddedEvent(): Unit = {
    eventBus.dispatch(CONTACT_ADDED_EVENT, NO_KEYS).block()
    assertThat(SFlux.fromPublisher(searchEngine.autoComplete(ACCOUNT_ID, "contact1", 1))
      .map(_.fields)
      .collectSeq().block().asJava)
      .containsExactlyInAnyOrder(CONTACT)
  }

  @Test
  def indexShouldBeIdempotent(): Unit = {
    eventBus.dispatch(CONTACT_ADDED_EVENT, NO_KEYS).block()
    eventBus.dispatch(CONTACT_ADDED_EVENT, NO_KEYS).block()
    assertThat(SFlux.fromPublisher(searchEngine.autoComplete(ACCOUNT_ID, "contact1", 1))
      .map(_.fields)
      .collectSeq().block().asJava)
      .containsExactlyInAnyOrder(CONTACT)
  }

}
