package com.linagora.tmail.james.jmap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.RabbitMQExtension.IsolationPolicy.WEAK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.TEN_SECONDS;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.jmap.api.model.AccountId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.linagora.tmail.james.jmap.contact.EmailAddressContactMessageHandler;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngine;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Receiver;


public class RabbitMQEmailAddressContactSubscriberTest {

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(WEAK);

    private EmailAddressContactSearchEngine searchEngine;
    private RabbitMQEmailAddressContactSubscriber subscriber;
    private String aqmpContactExchange;
    private String aqmpContactDeadLetterQueue;


    @BeforeEach
    void setup() {
        String aqmpSuffix = UUID.randomUUID().toString();
        aqmpContactExchange = "AddressContactExchangeForTesting" + aqmpSuffix;
        String aqmpContactQueue = "AddressContactQueueForTesting" + aqmpSuffix;
        aqmpContactDeadLetterQueue = "TmailQueue-dead-letter-queue-" + aqmpContactQueue;

        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("address.contact.exchange", aqmpContactExchange);
        configuration.addProperty("address.contact.queue", aqmpContactQueue);

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

    @ParameterizedTest
    @ValueSource(strings = {
        "{ " +
            "   \"type\": \"addition\"," +
            "   \"scope\": \"user\", " +
            "   \"owner\" : \"invalid!@#$%^&(\"," +
            "   \"entry\": {" +
            "        \"address\": \"alice@domain.tld\"," +
            "        \"firstname\": \"Alice\"," +
            "        \"surname\": \"Watson\"" +
            "    }" +
            "}",
        "{ " +
            "   \"type\": \"addition\"," +
            "   \"scope\": \"domain\", " +
            "   \"owner\" : \"domain!@#$%^&(\"," +
            "   \"entry\": {" +
            "        \"address\": \"alice@domain.tld\"," +
            "        \"firstname\": \"Alice\"," +
            "        \"surname\": \"Watson\"" +
            "    }" +
            "}",
        "",
        "1213"})
    void invalidMessageShouldBeDirectToDeadLetterQueue(String invalidMessage) {
        List<String> deadLetterMessages = new ArrayList<>();
        deadLetterMessageFlux()
            .subscribe(deadLetterMessages::add);

        sendMessage(invalidMessage);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(deadLetterMessages).hasSize(1));
    }

    private void sendMessage(String message) {
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(
                aqmpContactExchange,
                EMPTY_ROUTING_KEY,
                message.getBytes(UTF_8))))
            .block();
    }

    private Flux<String> deadLetterMessageFlux() {
        return Flux.using(() -> rabbitMQExtension.getReceiverProvider().createReceiver(),
                receiver -> receiver.consumeAutoAck(aqmpContactDeadLetterQueue),
                Receiver::close)
            .map(delivery -> new String(delivery.getBody(), UTF_8));
    }

}
