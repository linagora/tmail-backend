package com.linagora.tmail.james.jmap.contact;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.RabbitMQExtension.IsolationPolicy.WEAK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TEN_SECONDS;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.jmap.api.model.AccountId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;

class OpenPaasContactsConsumerTest {

    @RegisterExtension
    static OpenPaasServerExtension openPaasServerExtension = new OpenPaasServerExtension();

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(WEAK);

    private EmailAddressContactSearchEngine searchEngine;
    private OpenPaasRestClient restClient;
    private OpenPaasContactsConsumer consumer;

    @BeforeEach
    void setup() throws URISyntaxException {
        searchEngine = new InMemoryEmailAddressContactSearchEngine();
        restClient = new OpenPaasRestClient(
            new OpenPaasConfiguration(
                openPaasServerExtension.getBaseUrl(),
                "admin",
                "admin")
        );
        consumer = new OpenPaasContactsConsumer(rabbitMQExtension.getReceiverProvider(),
            rabbitMQExtension.getSender(),
            rabbitMQExtension.getRabbitMQ().withQuorumQueueConfiguration(),
            searchEngine, restClient);

        consumer.start();
    }

    @AfterEach
    void afterEach() throws IOException {
        consumer.close();
    }

    @Test
    void consumeMessageShouldNotCrashOnInvalidMessages() throws InterruptedException {
        IntStream.range(0, 10).forEach(i -> sendMessage("BAD_PAYLOAD" + i));

        TimeUnit.MILLISECONDS.sleep(100);

        sendMessage("""
            {
                "bookId": "abc0a663bdaffe0026290xyz",
                "bookName": "contacts",
                "contactId": "fd9b3c98-fc77-4187-92ac-d9f58d400968",
                "userId": "abc0a663bdaffe0026290xyz",
                "vcard": [
                "vcard",
                  [
                     [ "version", {}, "text", "4.0" ],
                     [ "kind",    {}, "text", "individual" ],
                     [ "fn",      {}, "text", "Jane Doe" ],
                     [ "email",   {}, "text", "jhon@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "jhon", 10))
                    .collectList().block())
                .hasSize(1));
    }

    @Test
    void contactShouldBeIndexedWhenContactUserAddedMessage() {
        sendMessage("""
            {
                "bookId": "abc0a663bdaffe0026290xyz",
                "bookName": "contacts",
                "contactId": "fd9b3c98-fc77-4187-92ac-d9f58d400968",
                "userId": "abc0a663bdaffe0026290xyz",
                "vcard": [
                "vcard",
                  [
                     [ "version", {}, "text", "4.0" ],
                     [ "kind",    {}, "text", "individual" ],
                     [ "fn",      {}, "text", "Jane Doe" ],
                     [ "email",   {}, "text", "jhon@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "jhon", 10))
                    .collectList().block())
                .hasSize(1));
    }

    private void sendMessage(String message) {
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(
                OpenPaasContactsConsumer.EXCHANGE_NAME,
                EMPTY_ROUTING_KEY,
                message.getBytes(UTF_8))))
            .block();
    }
}