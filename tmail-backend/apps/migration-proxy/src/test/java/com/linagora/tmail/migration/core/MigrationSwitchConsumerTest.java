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
 ********************************************************************/

package com.linagora.tmail.migration.core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.backends.rabbitmq.RabbitMQExtension.IsolationPolicy.WEAK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Set;

import org.apache.james.DisconnectorNotifier;
import org.apache.james.DisconnectorNotifier.MultipleUserRequest;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.core.Username;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.ScheduledReconnectionHandler;
import com.rabbitmq.client.Channel;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;

/**
 * Exercises {@link MigrationSwitchConsumer} against a real (Dockerized) RabbitMQ, per this project's
 * convention for testing AMQP consumers (see {@code ManagedRabbitMQConsumerTest}).
 */
class MigrationSwitchConsumerTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Username ALICE = Username.of("alice@linagora.com");
    private static final Username BOB = Username.of("btellier@linagora.com");

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(WEAK);

    private MigratedUsersRepository migratedUsersRepository;
    private DisconnectorNotifier disconnectorNotifier;
    private MigrationSwitchConsumer consumer;

    @BeforeEach
    void setUp() {
        migratedUsersRepository = new MemoryMigratedUsersRepository();
        disconnectorNotifier = mock(DisconnectorNotifier.class);
        consumer = new MigrationSwitchConsumer(rabbitMQExtension.getRabbitChannelPool(),
            new MigrationSwitchService(migratedUsersRepository, disconnectorNotifier));
        consumer.init();
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    private void publish(String payload) {
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(MigrationSwitchConsumer.EXCHANGE,
                MigrationSwitchConsumer.ROUTING_KEY, payload.getBytes(UTF_8))))
            .block();
    }

    private long consumerCount() throws Exception {
        try (Channel channel = rabbitMQExtension.getConnectionPool().getResilientConnection().block().createChannel()) {
            return channel.consumerCount(MigrationSwitchConsumer.QUEUE);
        }
    }

    @Test
    void shouldMarkUserAsMigratedWhenConsumingAValidMessage() {
        publish("{\"migratedUser\":{\"mailAddress\": \"" + BOB.asString() + "\"}}");

        await().atMost(TIMEOUT)
            .untilAsserted(() -> assertThat(migratedUsersRepository.isMigrated(BOB).block()).isTrue());
    }

    @Test
    void shouldDisconnectUserWhenConsumingAValidMessage() {
        publish("{\"migratedUser\":{\"mailAddress\": \"" + BOB.asString() + "\"}}");

        await().atMost(TIMEOUT)
            .untilAsserted(() -> verify(disconnectorNotifier).disconnect(MultipleUserRequest.of(Set.of(BOB))));
    }

    @Test
    void shouldIgnoreMessagesPublishedWithAnotherRoutingKey() {
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(MigrationSwitchConsumer.EXCHANGE, "another-routing-key",
                ("{\"migratedUser\":{\"mailAddress\": \"" + ALICE.asString() + "\"}}").getBytes(UTF_8))))
            .block();
        publish("{\"migratedUser\":{\"mailAddress\": \"" + BOB.asString() + "\"}}");

        await().atMost(TIMEOUT)
            .untilAsserted(() -> assertThat(migratedUsersRepository.isMigrated(BOB).block()).isTrue());
        assertThat(migratedUsersRepository.isMigrated(ALICE).block()).isFalse();
        assertThat(migratedUsersRepository.listMigratedUsers().collectList().block()).containsExactly(BOB);
    }

    @Test
    void shouldDropMalformedPayloadWithoutFailingSubsequentMessages() {
        publish("not json");
        publish("{\"migratedUser\":{\"mailAddress\": \"" + BOB.asString() + "\"}}");

        await().atMost(TIMEOUT)
            .untilAsserted(() -> assertThat(migratedUsersRepository.isMigrated(BOB).block()).isTrue());
    }

    @Test
    void shouldDropMessageWithUnparsableMailAddressWithoutFailingSubsequentMessages() {
        publish("{\"migratedUser\":{\"mailAddress\": \"not-an-address\"}}");
        publish("{\"migratedUser\":{\"mailAddress\": \"" + BOB.asString() + "\"}}");

        await().atMost(TIMEOUT)
            .untilAsserted(() -> assertThat(migratedUsersRepository.isMigrated(BOB).block()).isTrue());
    }

    @Test
    void shouldResumeConsumingWhenScheduledReconnectionDetectsMissingConsumer() throws Exception {
        ScheduledReconnectionHandler scheduledReconnectionHandler = new ScheduledReconnectionHandler(
            Set.of(consumer),
            rabbitMQExtension.getRabbitMQ().getConfiguration(),
            rabbitMQExtension.getConnectionPool(),
            new ScheduledReconnectionHandler.ScheduledReconnectionHandlerConfiguration(true, Duration.ofMillis(100)),
            Set.of(MigrationSwitchConsumer.QUEUE));

        scheduledReconnectionHandler.start();
        try {
            consumer.close();
            await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(consumerCount()).isEqualTo(1));

            publish("{\"migratedUser\":{\"mailAddress\": \"" + BOB.asString() + "\"}}");

            await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(migratedUsersRepository.isMigrated(BOB).block()).isTrue());
        } finally {
            scheduledReconnectionHandler.stop();
        }
    }
}
