package com.linagora.tmail.mailet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import reactor.core.publisher.Mono;
import static com.google.common.base.Predicates.instanceOf;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.TmailContactUserAddedEvent;
import com.linagora.tmail.mailets.IndexContacts;

class IndexContactsTest {
    private static class TestEventListener implements EventListener.ReactiveGroupEventListener {
        private final List<Event> events = new ArrayList<>();

        @Override
        public Mono<Void> reactiveEvent(Event event) {
            events.add(event);
            return Mono.empty();
        }

        @Override
        public Group getDefaultGroup() {
            return new Group();
        }

        public List<Event> receivedEvents() {
            return events;
        }

        public List<ContactFields> receivedContacts() {
            return events.stream()
                .filter(instanceOf(TmailContactUserAddedEvent.class))
                .map(e -> ((TmailContactUserAddedEvent) e).contact())
                .toList();
        }
    }

    private static final AttributeName ATTRIBUTE_NAME = AttributeName.of("AttributeValue1");

    private static final MailetConfig MAILET_CONFIG = FakeMailetConfig.builder()
        .mailetName("IndexContacts")
        .setProperty("attribute", ATTRIBUTE_NAME.asString())
        .build();

    private static final String SENDER_WITH_BAD_ADDRESS = "BAD_ADDRESS";
    private static final String SENDER = "sender1@domain.tld";
    private static final String RECIPIENT = "recipient1@domain.tld";
    private static final String RECIPIENT2 = "recipient2@domain.tld";
    private static final String RECIPIENT3 = "recipient3@domain.tld";
    private static final String RECIPIENT_WITH_BAD_ADDRESS = "RECIPIENT_BAD_ADDRESS";


    private EventBus eventBus;
    private IndexContacts mailet;
    private ObjectMapper objectMapper;
    private final TestEventListener eventListener = new TestEventListener();

    @BeforeEach
    void setup() {
        eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), RetryBackoffConfiguration.DEFAULT, new MemoryEventDeadLetters());
        eventBus.register(eventListener);
        mailet = new IndexContacts(eventBus);
        objectMapper = new ObjectMapper()
            .registerModule(new GuavaModule());
    }

    @Test
    void initShouldThrowWhenNoAttributeParameter() {
        var mailetConfig  = FakeMailetConfig.builder()
            .mailetName("IndexContacts")
            .build();

        assertThatThrownBy(() -> mailet.init(mailetConfig))
            .isInstanceOf(MailetException.class);
    }

    @Test
    void initShouldThrowWhenAttributeParameterIsBlank() {
        var mailetConfig = FakeMailetConfig.builder()
            .mailetName("IndexContacts")
            .setProperty("attribute", " ")
            .build();

        assertThatThrownBy(() -> mailet.init(mailetConfig))
            .isInstanceOf(MailetException.class);
    }

    @Test
    void serviceShouldDispatchEventWhenAttributeValueIsValid() throws MessagingException {
        mailet.init(MAILET_CONFIG);

        FakeMail mail = FakeMail.builder()
            .name("mail1")
            .attribute(ATTRIBUTE_NAME.withValue(AttributeValue.of(
                    """
                        {
                            "userEmail": "%s",
                            "emails": ["%s", "%s"]
                        }
                        """.formatted(SENDER, RECIPIENT, RECIPIENT2)
                ))
            ).build();

        mailet.service(mail);
        assertThat(eventListener.receivedContacts())
            .containsExactlyInAnyOrder(new ContactFields(new MailAddress(RECIPIENT), "", ""),
                                       new ContactFields(new MailAddress(RECIPIENT2), "", ""));
    }

    @Test
    void serviceShouldNotIndexContactsWhenUserEmailIsInvalid() throws MessagingException {
        mailet.init(MAILET_CONFIG);

        FakeMail mail = FakeMail.builder()
            .name("mail1")
            .attribute(ATTRIBUTE_NAME.withValue(AttributeValue.of(
                    """
                        {
                            "userEmail": "%s",
                            "emails": ["%s"]
                        }
                        """.formatted(SENDER_WITH_BAD_ADDRESS, RECIPIENT)
                ))
            ).build();

        mailet.service(mail);
        assertThat(eventListener.receivedContacts()).isEmpty();
    }

    @Test
    void serviceShouldNotIndexInvalidContactEmails() throws MessagingException {
        mailet.init(MAILET_CONFIG);

        FakeMail mail = FakeMail.builder()
            .name("mail1")
            .attribute(ATTRIBUTE_NAME.withValue(AttributeValue.of(
                    """
                        {
                            "userEmail": "%s",
                            "emails": ["%s", "%s"]
                        }
                        """.formatted(SENDER, RECIPIENT_WITH_BAD_ADDRESS, RECIPIENT)
                ))
            ).build();

        mailet.service(mail);
        assertThat(eventListener.receivedContacts())
            .containsExactlyInAnyOrder(new ContactFields(new MailAddress(RECIPIENT), "", ""));
    }

    @Test
    void serviceShouldNotDispatchEventWhenUserEmailsIsEmpty() throws MessagingException {
        mailet.init(MAILET_CONFIG);

        FakeMail mail = FakeMail.builder()
            .name("mail1")
            .attribute(ATTRIBUTE_NAME.withValue(AttributeValue.of(
                    """
                        {
                            "userEmail": "%s",
                            "emails": []
                        }
                        """.formatted(SENDER)
                ))
            ).build();

        mailet.service(mail);
        assertThat(eventListener.receivedEvents()).isEmpty();
    }

    @Test
    void serviceShouldNotClearAttributeValueAfterConsumption() throws MessagingException {
        mailet.init(MAILET_CONFIG);

        AttributeValue<String> attributeValue = AttributeValue.of(
            """
                {
                    "userEmail": "%s",
                    "emails": ["%s"]
                }
                """.formatted(SENDER, RECIPIENT)
        );

        FakeMail mail = FakeMail.builder()
            .name("mail1")
            .attribute(ATTRIBUTE_NAME.withValue(attributeValue))
            .build();

        mailet.service(mail);
        assertThatJson(getAttributeValue(mail).get())
            .isEqualTo(attributeValue.value());
    }

    private Optional<String> getAttributeValue(Mail mail) {
        return mail.getAttribute(ATTRIBUTE_NAME)
            .map(Attribute::getValue)
            .flatMap(a -> a.valueAs(String.class));
    }

}
