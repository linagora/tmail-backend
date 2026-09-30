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

package com.linagora.tmail.saas.rabbitmq.deletion;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.james.adapter.mailbox.MailboxUserDeletionTaskStep;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.webadmin.service.DeleteUserDataService;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.reactivestreams.Publisher;

import com.linagora.tmail.saas.rabbitmq.TWPCommonRabbitMQConfiguration;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;

public class TWPUserDeletionConsumerTest {
    private static final Username ALICE = Username.of("alice@twake.app");
    private static final Username BOB = Username.of("bob@twake.app");
    private static final Username FAILING_USER = Username.of("failing@twake.app");
    private static final TWPUserDeletionRabbitMQConfiguration DEFAULT_CONFIGURATION = new TWPUserDeletionRabbitMQConfiguration(
        "auth", "user.deleted", "b2b", "domain.user.deleted");

    private static final DeleteUserDataTaskStep FAILING_STEP = new DeleteUserDataTaskStep() {
        @Override
        public StepName name() {
            return new StepName("FailingStep");
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Publisher<Void> deleteUserData(Username username) {
            if (username.equals(FAILING_USER)) {
                return Mono.error(new RuntimeException("Simulated step failure"));
            }
            return Mono.empty();
        }
    };

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.WEAK);

    private final ConditionFactory awaitAtMost = Awaitility.with()
        .pollInterval(Duration.ofMillis(200))
        .await()
        .atMost(10, TimeUnit.SECONDS);

    private InMemoryMailboxManager mailboxManager;
    private DeleteUserDataService deleteUserDataService;
    private TWPUserDeletionConsumer.UserDeletionConsumerConfig consumerConfig;
    private TWPUserDeletionConsumer testee;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        StoreSubscriptionManager subscriptionManager = new StoreSubscriptionManager(mailboxManager.getMapperFactory(),
            mailboxManager.getMapperFactory(), resources.getEventBus());
        deleteUserDataService = new DeleteUserDataService(Set.of(
            new MailboxUserDeletionTaskStep(mailboxManager, subscriptionManager),
            FAILING_STEP));

        String queue = "tmail-user-deletion-" + UUID.randomUUID();
        consumerConfig = new TWPUserDeletionConsumer.UserDeletionConsumerConfig(queue, queue + "-dead-letter");
        testee = startConsumer(DEFAULT_CONFIGURATION);
    }

    private TWPUserDeletionConsumer startConsumer(TWPUserDeletionRabbitMQConfiguration userDeletionRabbitMQConfiguration) throws Exception {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(rabbitMQExtension.getRabbitMQ().amqpUri())
            .managementUri(rabbitMQExtension.getRabbitMQ().managementUri())
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();

        TWPUserDeletionConsumer consumer = new TWPUserDeletionConsumer(
            rabbitMQExtension.getRabbitChannelPool(),
            rabbitMQConfiguration,
            new TWPCommonRabbitMQConfiguration(Optional.empty(), Optional.empty(), false),
            userDeletionRabbitMQConfiguration,
            consumerConfig,
            deleteUserDataService);
        consumer.init();
        return consumer;
    }

    @AfterEach
    void tearDown() {
        testee.close();
    }

    @Test
    void b2cDeletionEventShouldDeleteUserData() throws Exception {
        createInbox(ALICE);

        publish("auth", "user.deleted", deletionEvent(ALICE));

        awaitAtMost.untilAsserted(() -> assertThat(mailboxesOf(ALICE)).isEmpty());
    }

    @Test
    void b2bDeletionEventShouldDeleteUserData() throws Exception {
        createInbox(ALICE);

        publish("b2b", "domain.user.deleted", deletionEvent(ALICE));

        awaitAtMost.untilAsserted(() -> assertThat(mailboxesOf(ALICE)).isEmpty());
    }

    @Test
    void deletionEventShouldNotDeleteOtherUsersData() throws Exception {
        createInbox(ALICE);
        createInbox(BOB);

        publish("auth", "user.deleted", deletionEvent(ALICE));

        awaitAtMost.untilAsserted(() -> assertThat(mailboxesOf(ALICE)).isEmpty());
        assertThat(mailboxesOf(BOB)).hasSize(1);
    }

    @Test
    void configuredExchangesAndRoutingKeysShouldBeUsed() throws Exception {
        testee.close();
        testee = startConsumer(new TWPUserDeletionRabbitMQConfiguration("customB2c", "custom.user.deleted", "customB2b", "custom.domain.user.deleted"));
        createInbox(ALICE);
        createInbox(BOB);

        publish("customB2c", "custom.user.deleted", deletionEvent(ALICE));
        publish("customB2b", "custom.domain.user.deleted", deletionEvent(BOB));

        awaitAtMost.untilAsserted(() -> {
            assertThat(mailboxesOf(ALICE)).isEmpty();
            assertThat(mailboxesOf(BOB)).isEmpty();
        });
    }

    @Test
    void deletingAUserWithoutDataShouldSucceed() throws Exception {
        createInbox(ALICE);

        publish("auth", "user.deleted", deletionEvent(Username.of("unknown@twake.app")));
        publish("auth", "user.deleted", deletionEvent(ALICE));

        awaitAtMost.untilAsserted(() -> assertThat(mailboxesOf(ALICE)).isEmpty());
        assertThat(messageCount(consumerConfig.deadLetterQueue())).isZero();
    }

    @Test
    void failingDeletionShouldBeDeadLettered() throws Exception {
        publish("auth", "user.deleted", deletionEvent(FAILING_USER));

        awaitAtMost.untilAsserted(() -> assertThat(messageCount(consumerConfig.deadLetterQueue())).isEqualTo(1));
    }

    @Test
    void eventWithoutInternalEmailShouldBeDeadLettered() throws Exception {
        publish("auth", "user.deleted", "{\"userId\": \"alice\", \"type\": \"user.deleted\"}");

        awaitAtMost.untilAsserted(() -> assertThat(messageCount(consumerConfig.deadLetterQueue())).isEqualTo(1));
    }

    @Test
    void invalidEventShouldNotStopTheConsumer() throws Exception {
        createInbox(ALICE);

        publish("auth", "user.deleted", "{ invalid json }");
        publish("auth", "user.deleted", deletionEvent(ALICE));

        awaitAtMost.untilAsserted(() -> assertThat(mailboxesOf(ALICE)).isEmpty());
    }

    private String deletionEvent(Username username) {
        return String.format("""
            {
                "emitter": "ldap-rest",
                "type": "user.deleted",
                "userId": "%s",
                "internalEmail": "%s",
                "workplaceFqdn": "twake.app",
                "reason": "user_request",
                "reasonCode": 1,
                "deletedAt": "2026-09-30T10:00:00Z"
            }
            """, username.getLocalPart(), username.asString());
    }

    private void createInbox(Username username) throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(username);
        mailboxManager.createMailbox(MailboxPath.inbox(username), session);
    }

    private List<MailboxPath> mailboxesOf(Username username) {
        MailboxSession session = mailboxManager.createSystemSession(username);
        return mailboxManager.search(MailboxQuery.privateMailboxesBuilder(session).build(), session)
            .map(MailboxMetaData::getPath)
            .collectList()
            .block();
    }

    private long messageCount(String queue) throws Exception {
        Connection connection = rabbitMQExtension.getConnectionPool().getResilientConnection().block();
        try (Channel channel = connection.createChannel()) {
            return channel.messageCount(queue);
        }
    }

    private void publish(String exchange, String routingKey, String message) {
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(exchange, routingKey, message.getBytes(UTF_8))))
            .block();
    }
}
