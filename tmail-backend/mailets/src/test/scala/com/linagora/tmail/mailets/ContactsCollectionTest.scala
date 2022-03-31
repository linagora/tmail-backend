package com.linagora.tmail.mailets

import java.util

import com.linagora.tmail.mailets.ContactsCollectionTest.{ATTRIBUTE_NAME, MAILET_CONFIG, RECIPIENT, SENDER}
import org.apache.james.core.builder.MimeMessageBuilder
import org.apache.james.events.EventListener.ReactiveGroupEventListener
import org.apache.james.events.delivery.InVmEventDelivery
import org.apache.james.events.{Event, EventBus, Group, InVMEventBus, MemoryEventDeadLetters, RetryBackoffConfiguration}
import org.apache.james.metrics.tests.RecordingMetricFactory
import org.apache.mailet.base.test.{FakeMail, FakeMailetConfig}
import org.apache.mailet.{AttributeName, MailetConfig, MailetException}
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.junit.jupiter.api.{BeforeEach, Test}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

object ContactsCollectionTest {
  val ATTRIBUTE_NAME: AttributeName = AttributeName.of("AttributeValue1")
  val MAILET_CONFIG: MailetConfig = FakeMailetConfig.builder()
    .mailetName("ContactsCollection")
    .setProperty("attribute", ATTRIBUTE_NAME.asString())
    .build()

  val SENDER: String = "sender1@domain.tld"
  val RECIPIENT: String = "recipient1@domain.tld"
}

class TestEventListener(events: util.ArrayList[Event] = new util.ArrayList[Event]()) extends ReactiveGroupEventListener {

  override def reactiveEvent(event: Event): Publisher[Void] = SMono.just(events.add(event)).`then`()

  override def getDefaultGroup: Group = new Group

  def eventReceived(): util.ArrayList[Event] = events
}

class ContactsCollectionTest {
  var mailet: ContactsCollection = _
  var eventBus: EventBus = _
  var eventListener: TestEventListener = new TestEventListener()

  @BeforeEach
  def setup(): Unit = {
    eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory), RetryBackoffConfiguration.DEFAULT, new MemoryEventDeadLetters())
    eventBus.register(eventListener)
    mailet = new ContactsCollection(eventBus)
  }

  @Test
  def initShouldThrowWhenNoAttributeParameter(): Unit = {
    val mailetConfig: FakeMailetConfig = FakeMailetConfig.builder()
      .mailetName("ContactsCollection")
      .build()

    assertThatThrownBy(() => mailet.init(mailetConfig))
      .isInstanceOf(classOf[MailetException])
  }

  @Test
  def initShouldNotThrowWithAllParameters(): Unit = {
    val mailetConfig: FakeMailetConfig = FakeMailetConfig.builder()
      .mailetName("ContactsCollection")
      .setProperty("attribute", "attributeValue")
      .build()

    assertThatCode(() => mailet.init(mailetConfig))
      .doesNotThrowAnyException()
  }

  @Test
  def serviceShouldDispatchEventWhenHasRecipients(): Unit = {
    mailet.init(MAILET_CONFIG)

    val mail: FakeMail = FakeMail.builder()
      .name("mail1")
      .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
        .setSender(SENDER)
        .addToRecipient(RECIPIENT)
        .setSubject("Subject 01")
        .setText("Content mail 123"))
      .sender(SENDER)
      .recipient(RECIPIENT)
      .build()

    mailet.service(mail)
    assertThat(eventListener.eventReceived()).hasSize(1)
  }

  @Test
  def serviceShouldAddTheAttribute(): Unit = {
    mailet.init(MAILET_CONFIG)

    val mail: FakeMail = FakeMail.builder()
      .name("mail1")
      .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
        .setSender(SENDER)
        .addToRecipient(RECIPIENT)
        .setSubject("Subject 01")
        .setText("Content mail 123"))
      .sender(SENDER)
      .recipient(RECIPIENT)
      .build()

    mailet.service(mail)

    val attribute = mail.getAttribute(ATTRIBUTE_NAME)

    assertThat(attribute).isPresent
    assertThat(attribute.get.getValue.value().asInstanceOf[String])
      .isEqualTo(s"[$RECIPIENT]")
  }

}
