/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 *******************************************************************/

package com.linagora.tmail.rabbitmq;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.backends.rabbitmq.RabbitMQExtension.IsolationPolicy.WEAK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.rabbitmq.client.BuiltinExchangeType;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Receiver;

class ManagedRabbitMQConsumerTest {

    private static final String ROUTING_KEY = "routing-key";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(WEAK);

    private ManagedRabbitMQConsumer consumer;
    private QueueDeclaration spec;
    private ConcurrentLinkedQueue<String> consumedMessages;
    private AtomicBoolean handlerShouldFail;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        spec = QueueDeclaration.builder()
            .binding("exchange-" + suffix, ROUTING_KEY)
            .queue("queue-" + suffix)
            .deadLetterExchange("dead-letter-exchange-" + suffix, BuiltinExchangeType.FANOUT)
            .deadLetterQueue("dead-letter-queue-" + suffix)
            .build();
        consumedMessages = new ConcurrentLinkedQueue<>();
        handlerShouldFail = new AtomicBoolean(false);
        consumer = consumer(spec);
        consumer.init();
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    private ManagedRabbitMQConsumer consumer(QueueDeclaration queueDeclaration) {
        return new ManagedRabbitMQConsumer.Factory(rabbitMQExtension.getRabbitChannelPool())
            .create(ManagedRabbitMQConsumer.Parameters.builder()
                .queueDeclaration(queueDeclaration)
                .handleDelivery(delivery -> {
                    if (handlerShouldFail.get()) {
                        return Mono.error(new RuntimeException("Handler failure"));
                    }
                    consumedMessages.add(new String(delivery.getBody(), UTF_8));
                    return Mono.empty();
                })
                .build());
    }

    private void publish(String exchange, String routingKey, String payload) {
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(exchange, routingKey, payload.getBytes(UTF_8))))
            .block();
    }

    private List<String> deadLetteredMessages() {
        return Flux.using(rabbitMQExtension.getReceiverProvider()::createReceiver,
                receiver -> receiver.consumeAutoAck(spec.deadLetterQueue()),
                Receiver::close)
            .map(delivery -> new String(delivery.getBody(), UTF_8))
            .take(Duration.ofSeconds(1))
            .collectList()
            .block();
    }

    @Test
    void shouldConsumeMessagesPublishedOnTheBoundExchange() {
        publish(spec.bindings().get(0).exchange(), ROUTING_KEY, "hello");

        await().atMost(TIMEOUT)
            .untilAsserted(() -> assertThat(consumedMessages).containsExactly("hello"));
    }

    @Test
    void shouldIgnoreMessagesPublishedWithAnotherRoutingKey() {
        publish(spec.bindings().get(0).exchange(), "another-routing-key", "hello");
        publish(spec.bindings().get(0).exchange(), ROUTING_KEY, "expected");

        await().atMost(TIMEOUT)
            .untilAsserted(() -> assertThat(consumedMessages).containsExactly("expected"));
    }

    @Test
    void shouldDeadLetterMessagesWhenHandlerFails() {
        handlerShouldFail.set(true);
        publish(spec.bindings().get(0).exchange(), ROUTING_KEY, "poisoned");

        await().atMost(TIMEOUT)
            .untilAsserted(() -> assertThat(deadLetteredMessages()).containsExactly("poisoned"));
    }

    @Test
    void shouldNotRequeueMessagesWhenHandlerFails() {
        handlerShouldFail.set(true);
        publish(spec.bindings().get(0).exchange(), ROUTING_KEY, "poisoned");
        await().atMost(TIMEOUT)
            .untilAsserted(() -> assertThat(deadLetteredMessages()).hasSize(1));

        handlerShouldFail.set(false);
        publish(spec.bindings().get(0).exchange(), ROUTING_KEY, "healthy");

        await().atMost(TIMEOUT)
            .untilAsserted(() -> assertThat(consumedMessages).containsExactly("healthy"));
    }

    @Test
    void restartShouldKeepConsumingMessages() {
        consumer.restart();

        publish(spec.bindings().get(0).exchange(), ROUTING_KEY, "hello");

        await().atMost(TIMEOUT)
            .untilAsserted(() -> assertThat(consumedMessages).containsExactly("hello"));
    }

    @Test
    void closeShouldStopConsumingMessages() throws Exception {
        consumer.close();

        publish(spec.bindings().get(0).exchange(), ROUTING_KEY, "hello");
        Thread.sleep(500);

        assertThat(consumedMessages).isEmpty();
    }

    @Test
    void restartAfterCloseShouldResumeConsumingMessages() {
        consumer.close();
        consumer.restart();

        publish(spec.bindings().get(0).exchange(), ROUTING_KEY, "hello");

        await().atMost(TIMEOUT)
            .untilAsserted(() -> assertThat(consumedMessages).containsExactly("hello"));
    }

    @Test
    void declareShouldBeIdempotent() {
        consumer.declare().block();

        publish(spec.bindings().get(0).exchange(), ROUTING_KEY, "hello");

        await().atMost(TIMEOUT)
            .untilAsserted(() -> assertThat(consumedMessages).containsExactly("hello"));
    }

    @Test
    void shouldConsumeMessagesFromEveryBoundExchange() {
        String suffix = UUID.randomUUID().toString();
        QueueDeclaration multiBindingSpec = QueueDeclaration.builder()
            .binding("first-exchange-" + suffix, "first-routing-key")
            .binding("second-exchange-" + suffix, "second-routing-key")
            .queue("multi-binding-queue-" + suffix)
            .deadLetterQueue("multi-binding-dead-letter-queue-" + suffix)
            .build();
        ManagedRabbitMQConsumer multiBindingConsumer = consumer(multiBindingSpec);
        multiBindingConsumer.init();

        try {
            publish("first-exchange-" + suffix, "first-routing-key", "first");
            publish("second-exchange-" + suffix, "second-routing-key", "second");

            await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(consumedMessages).containsExactlyInAnyOrder("first", "second"));
        } finally {
            multiBindingConsumer.close();
        }
    }
}
