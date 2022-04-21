package com.linagora.tmail.james.jmap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.backends.rabbitmq.RabbitMQExtension.IsolationPolicy.WEAK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.TEN_SECONDS;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.core.MailAddress;
import org.apache.james.jmap.api.model.AccountId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.jmap.contact.Addition$;
import com.linagora.tmail.james.jmap.contact.ContactOwner;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactMessage;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactMessageHandler;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.contact.MessageEntry;
import com.linagora.tmail.james.jmap.contact.User$;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import scala.jdk.javaapi.OptionConverters;


public class RabbitMQEmailAddressContactSubscriberTest {

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(WEAK);

    private EmailAddressContactSearchEngine searchEngine;
    private RabbitMQEmailAddressContactSubscriber subscriber;
    private PropertiesConfiguration configuration;

    @BeforeEach
    void setup() {
        configuration = new PropertiesConfiguration();
        String rabbitmqConfigSuffix = UUID.randomUUID().toString();
        configuration.addProperty("address.contact.exchange", "AddressContactExchangeForTesting" + rabbitmqConfigSuffix);
        configuration.addProperty("address.contact.queue", "AddressContactQueueForTesting" + rabbitmqConfigSuffix);
        configuration.addProperty("address.contact.routingKey", "AddressContactRoutingKeyForTesting" + rabbitmqConfigSuffix);

        searchEngine = new InMemoryEmailAddressContactSearchEngine();
        subscriber = new RabbitMQEmailAddressContactSubscriber(rabbitMQExtension.getReceiverProvider(),
            rabbitMQExtension.getSender(),
            new EmailAddressContactMessageHandler(searchEngine),
            configuration);
        subscriber.start();
    }

    @AfterEach
    void afterEach() {
        subscriber.close();
    }

    @Test
    void consumeQueueShouldReceivedNewMessage() {
        List<EmailAddressContactMessage> receivedMessage = new ArrayList<>();
        subscriber.receivedMessages()
            .subscribe(receivedMessage::add);

        sendMessage("{ " +
            "   \"type\": \"addition\"," +
            "   \"scope\": \"user\", " +
            "   \"owner\" : \"bob@domain.tld\"," +
            "   \"entry\": {" +
            "        \"address\": \"alice@domain.tld\"," +
            "        \"firstname\": \"Alice\"," +
            "        \"surname\": \"Watson\"" +
            "    }" +
            "}");

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(receivedMessage).hasSize(1)
                .allSatisfy(message ->
                    assertThat(message).isEqualTo(new EmailAddressContactMessage(Addition$.MODULE$, User$.MODULE$,
                        new ContactOwner("bob@domain.tld"),
                        new MessageEntry(new MailAddress("alice@domain.tld"),
                            OptionConverters.toScala(Optional.of("Alice")),
                            OptionConverters.toScala(Optional.of("Watson")))))));
    }

    @Test
    void consumeQueueShouldReceivedNewMessages() {
        List<EmailAddressContactMessage> receivedMessage = new ArrayList<>();
        subscriber.receivedMessages()
            .subscribe(receivedMessage::add);

        IntStream.range(0, 10)
            .forEach(i -> sendMessage(String.format("{ " +
                "   \"type\": \"addition\"," +
                "   \"scope\": \"user\", " +
                "   \"owner\" : \"bob@domain.tld\"," +
                "   \"entry\": {" +
                "        \"address\": \"alice%s@domain.tld\"," +
                "        \"firstname\": \"Alice\"," +
                "        \"surname\": \"Watson\"" +
                "    }" +
                "}", i)));

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(receivedMessage).hasSize(10));
    }

    @Test
    void consumeQueueShouldNotCrashOnInvalidMessage() throws InterruptedException {
        List<EmailAddressContactMessage> receivedMessage = new ArrayList<>();
        subscriber.receivedMessages()
            .subscribe(receivedMessage::add);

        sendMessage("BAD_PAYLOAD");
        TimeUnit.MILLISECONDS.sleep(100);
        sendMessage("{ " +
            "   \"type\": \"addition\"," +
            "   \"scope\": \"user\", " +
            "   \"owner\" : \"bob@domain.tld\"," +
            "   \"entry\": {" +
            "        \"address\": \"alice@domain.tld\"," +
            "        \"firstname\": \"Alice\"," +
            "        \"surname\": \"Watson\"" +
            "    }" +
            "}");

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(receivedMessage).hasSize(1));
    }

    @Test
    void consumeQueueShouldNotCrashOnInvalidMessages() throws InterruptedException {
        List<EmailAddressContactMessage> receivedMessage = new ArrayList<>();
        subscriber.receivedMessages()
            .subscribe(receivedMessage::add);

        IntStream.range(0, 10).forEach(i -> sendMessage("BAD_PAYLOAD" + i));
        TimeUnit.MILLISECONDS.sleep(100);

        sendMessage("{ " +
            "   \"type\": \"addition\"," +
            "   \"scope\": \"user\", " +
            "   \"owner\" : \"bob@domain.tld\"," +
            "   \"entry\": {" +
            "        \"address\": \"alice@domain.tld\"," +
            "        \"firstname\": \"Alice\"," +
            "        \"surname\": \"Watson\"" +
            "    }" +
            "}");

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(receivedMessage).hasSize(1));
    }

    @Test
    void consumeMessageShouldNotCrashOnInvalidMessages() throws InterruptedException {
        IntStream.range(0, 10).forEach(i -> sendMessage("BAD_PAYLOAD" + i));
        TimeUnit.MILLISECONDS.sleep(100);

        sendMessage("{ " +
            "   \"type\": \"addition\"," +
            "   \"scope\": \"user\", " +
            "   \"owner\" : \"bob@domain.tld\"," +
            "   \"entry\": {" +
            "        \"address\": \"alice@domain.tld\"," +
            "        \"firstname\": \"Alice\"," +
            "        \"surname\": \"Watson\"" +
            "    }" +
            "}");

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(Flux.from(searchEngine.autoComplete(AccountId.fromString("bob@domain.tld"), "alice"))
                .collectList().block())
                .hasSize(1));
    }

    @Nested
    class ContactMessageTest {

        @Test
        void contactShouldBeIndexedWhenContactUserAddedMessage() {
            sendMessage("{ " +
                "   \"type\": \"addition\"," +
                "   \"scope\": \"user\", " +
                "   \"owner\" : \"bob@domain.tld\"," +
                "   \"entry\": {" +
                "        \"address\": \"alice@domain.tld\"," +
                "        \"firstname\": \"Alice\"," +
                "        \"surname\": \"Watson\"" +
                "    }" +
                "}");

            await().timeout(TEN_SECONDS).untilAsserted(() ->
                assertThat(Flux.from(searchEngine.autoComplete(AccountId.fromString("bob@domain.tld"), "alice"))
                    .collectList().block())
                    .hasSize(1));
        }

        @Test
        void contactShouldBeUpdatedWhenContactUserAddedMessage() {
            sendMessage("{ " +
                "   \"type\": \"addition\"," +
                "   \"scope\": \"user\", " +
                "   \"owner\" : \"bob@domain.tld\"," +
                "   \"entry\": {" +
                "        \"address\": \"alice@domain.tld\"," +
                "        \"firstname\": \"Alice\"," +
                "        \"surname\": \"Watson\"" +
                "    }" +
                "}");

            await().timeout(TEN_SECONDS).untilAsserted(() ->
                assertThat(Flux.from(searchEngine.autoComplete(AccountId.fromString("bob@domain.tld"), "alice"))
                    .collectList().block())
                    .hasSize(1));


            sendMessage("{ " +
                "   \"type\": \"update\"," +
                "   \"scope\": \"user\", " +
                "   \"owner\" : \"bob@domain.tld\"," +
                "   \"entry\": {" +
                "        \"address\": \"alice@domain.tld\"," +
                "        \"firstname\": \"UpdateFirstName\"," +
                "        \"surname\": \"Watson\"" +
                "    }" +
                "}");

            await().timeout(TEN_SECONDS).untilAsserted(() ->
                assertThat(Flux.from(searchEngine.autoComplete(AccountId.fromString("bob@domain.tld"), "alice"))
                    .collectList().block())
                    .hasSize(1)
                    .allSatisfy(contact -> assertThat(contact.fields().firstname()).isEqualTo("UpdateFirstName"))
            );
        }

        @Test
        void contactShouldBeDeletedWhenContactUserRemovedMessage() {
            sendMessage("{ " +
                "   \"type\": \"addition\"," +
                "   \"scope\": \"user\", " +
                "   \"owner\" : \"bob@domain.tld\"," +
                "   \"entry\": {" +
                "        \"address\": \"alice@domain.tld\"," +
                "        \"firstname\": \"Alice\"," +
                "        \"surname\": \"Watson\"" +
                "    }" +
                "}");

            await().timeout(TEN_SECONDS).untilAsserted(() ->
                assertThat(Flux.from(searchEngine.autoComplete(AccountId.fromString("bob@domain.tld"), "alice"))
                    .collectList().block())
                    .hasSize(1));

            sendMessage("{ " +
                "   \"type\": \"removal\"," +
                "   \"scope\": \"user\", " +
                "   \"owner\" : \"bob@domain.tld\"," +
                "   \"entry\": {" +
                "        \"address\": \"alice@domain.tld\"," +
                "        \"firstname\": \"UpdateFirstName\"," +
                "        \"surname\": \"Watson\"" +
                "    }" +
                "}");

            await().timeout(TEN_SECONDS).untilAsserted(() ->
                assertThat(Flux.from(searchEngine.autoComplete(AccountId.fromString("bob@domain.tld"), "alice"))
                    .collectList().block())
                    .isEmpty());
        }

        @Test
        void contactShouldBeIndexedWhenContactDomainAddedMessage() {
            sendMessage("{ " +
                "   \"type\": \"addition\"," +
                "   \"scope\": \"domain\", " +
                "   \"owner\" : \"domain.tld\"," +
                "   \"entry\": {" +
                "        \"address\": \"alice@domain.tld\"," +
                "        \"firstname\": \"Alice\"," +
                "        \"surname\": \"Watson\"" +
                "    }" +
                "}");

            await().timeout(TEN_SECONDS).untilAsserted(() ->
                assertThat(Flux.from(searchEngine.autoComplete(AccountId.fromString("any@domain.tld"), "alice"))
                    .collectList().block())
                    .hasSize(1));
        }

        @Test
        void contactShouldBeUpdatedWhenContactDomainAddedMessage() {
            sendMessage("{ " +
                "   \"type\": \"addition\"," +
                "   \"scope\": \"domain\", " +
                "   \"owner\" : \"domain.tld\"," +
                "   \"entry\": {" +
                "        \"address\": \"alice@domain.tld\"," +
                "        \"firstname\": \"Alice\"," +
                "        \"surname\": \"Watson\"" +
                "    }" +
                "}");

            await().timeout(TEN_SECONDS).untilAsserted(() ->
                assertThat(Flux.from(searchEngine.autoComplete(AccountId.fromString("any@domain.tld"), "alice"))
                    .collectList().block())
                    .hasSize(1));


            sendMessage("{ " +
                "   \"type\": \"addition\"," +
                "   \"scope\": \"domain\", " +
                "   \"owner\" : \"domain.tld\"," +
                "   \"entry\": {" +
                "        \"address\": \"alice@domain.tld\"," +
                "        \"firstname\": \"UpdateFirstName\"," +
                "        \"surname\": \"Watson\"" +
                "    }" +
                "}");

            await().timeout(TEN_SECONDS).untilAsserted(() ->
                assertThat(Flux.from(searchEngine.autoComplete(AccountId.fromString("any@domain.tld"), "alice"))
                    .collectList().block())
                    .hasSize(1)
                    .allSatisfy(contact -> assertThat(contact.fields().firstname()).isEqualTo("UpdateFirstName"))
            );
        }

        @Test
        void contactShouldBeDeletedWhenContactDomainRemovedMessage() {
            sendMessage("{ " +
                "   \"type\": \"addition\"," +
                "   \"scope\": \"domain\", " +
                "   \"owner\" : \"domain.tld\"," +
                "   \"entry\": {" +
                "        \"address\": \"alice@domain.tld\"," +
                "        \"firstname\": \"Alice\"," +
                "        \"surname\": \"Watson\"" +
                "    }" +
                "}");

            await().timeout(TEN_SECONDS).untilAsserted(() ->
                assertThat(Flux.from(searchEngine.autoComplete(AccountId.fromString("any@domain.tld"), "alice"))
                    .collectList().block())
                    .hasSize(1));

            sendMessage("{ " +
                "   \"type\": \"removal\"," +
                "   \"scope\": \"domain\", " +
                "   \"owner\" : \"domain.tld\"," +
                "   \"entry\": {" +
                "        \"address\": \"alice@domain.tld\"," +
                "        \"firstname\": \"UpdateFirstName\"," +
                "        \"surname\": \"Watson\"" +
                "    }" +
                "}");

            await().timeout(TEN_SECONDS).untilAsserted(() ->
                assertThat(Flux.from(searchEngine.autoComplete(AccountId.fromString("any@domain.tld"), "alice"))
                    .collectList().block())
                    .isEmpty());
        }

        @Test
        void consumeMessageShouldNotCrashOnInvalidOwner() throws InterruptedException {
            // send invalid message (owner can not serialize)
            sendMessage("{ " +
                "   \"type\": \"addition\"," +
                "   \"scope\": \"user\", " +
                "   \"owner\" : \"invalid!@#$%^&(\"," +
                "   \"entry\": {" +
                "        \"address\": \"alice@domain.tld\"," +
                "        \"firstname\": \"Alice\"," +
                "        \"surname\": \"Watson\"" +
                "    }" +
                "}");
            TimeUnit.MILLISECONDS.sleep(100);

            // valid message
            sendMessage("{ " +
                "   \"type\": \"addition\"," +
                "   \"scope\": \"user\", " +
                "   \"owner\" : \"bob@domain.tld\"," +
                "   \"entry\": {" +
                "        \"address\": \"alice@domain.tld\"," +
                "        \"firstname\": \"Alice\"," +
                "        \"surname\": \"Watson\"" +
                "    }" +
                "}");

            await().timeout(ONE_MINUTE).untilAsserted(() ->
                assertThat(Flux.from(searchEngine.autoComplete(AccountId.fromString("bob@domain.tld"), "alice"))
                    .collectList().block())
                    .hasSize(1));
        }

    }

    private void sendMessage(String message) {
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(
                configuration.getString("address.contact.exchange"),
                configuration.getString("address.contact.routingKey"),
                message.getBytes(UTF_8))))
            .block();
    }

}
