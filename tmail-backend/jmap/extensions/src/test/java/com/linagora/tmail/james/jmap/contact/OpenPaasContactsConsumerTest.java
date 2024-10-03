package com.linagora.tmail.james.jmap.contact;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.RabbitMQExtension.IsolationPolicy.WEAK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TEN_SECONDS;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.jmap.api.model.AccountId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;

class OpenPaasContactsConsumerTest {

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(WEAK);

    private EmailAddressContactSearchEngine searchEngine;
    private OpenPaasContactsConsumer consumer;

    @BeforeEach
    void setup() throws URISyntaxException {
        searchEngine = new InMemoryEmailAddressContactSearchEngine();
        consumer = new OpenPaasContactsConsumer(rabbitMQExtension.getReceiverProvider(),
            rabbitMQExtension.getSender(),
            rabbitMQExtension.getRabbitMQ().withQuorumQueueConfiguration(),
            searchEngine);

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

        sendMessage("INVALID_PAYLOAD");

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(Flux.from(searchEngine.autoComplete(AccountId.fromString("bob@domain.tld"), "alice", 10))
                .collectList().block())
                .hasSize(1));
    }

    @Test
    void contactShouldBeIndexedWhenContactUserAddedMessage() {
        sendMessage("""
            {
                "firstname" : "Alice",
                "lastname"  : "Doe",
                "email"     : "bob@domain.tld",
                "username"  : "alice12"
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString("bob@domain.tld"), "alice", 10))
                    .collectList().block())
                .hasSize(1));
    }

    private void sendMessage(String message) {
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(
                OpenPaasContactsConsumer.TOPIC,
                EMPTY_ROUTING_KEY,
                message.getBytes(UTF_8))))
            .block();
    }
}