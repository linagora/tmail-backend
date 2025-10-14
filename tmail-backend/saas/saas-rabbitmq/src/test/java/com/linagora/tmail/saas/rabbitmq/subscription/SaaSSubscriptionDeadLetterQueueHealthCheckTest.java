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

package com.linagora.tmail.saas.rabbitmq.subscription;

import static com.linagora.tmail.saas.rabbitmq.subscription.SaaSDomainSubscriptionConsumer.SAAS_DOMAIN_SUBSCRIPTION_DEAD_LETTER_QUEUE;
import static com.linagora.tmail.saas.rabbitmq.subscription.SaaSSubscriptionConsumer.SAAS_SUBSCRIPTION_DEAD_LETTER_QUEUE;
import static com.linagora.tmail.saas.rabbitmq.subscription.SaaSSubscriptionRabbitMQConfiguration.TWP_SAAS_SUBSCRIPTION_EXCHANGE_DEFAULT;
import static com.rabbitmq.client.BuiltinExchangeType.DIRECT;
import static com.rabbitmq.client.MessageProperties.PERSISTENT_TEXT_PLAIN;
import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import org.apache.james.backends.rabbitmq.DockerRabbitMQ;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

class SaaSSubscriptionDeadLetterQueueHealthCheckTest {
    private static final String ROUTING_KEY_USER = "routingKeyUser";
    private static final String ROUTING_KEY_DOMAIN = "routingKeyDomain";

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.STRONG);

    public static final String TWP_VHOST = "twp-test";
    public static final ImmutableMap<String, Object> NO_QUEUE_DECLARE_ARGUMENTS = ImmutableMap.of();

    private Connection connection;
    private Channel channel;
    private SaaSSubscriptionDeadLetterQueueHealthCheck testee;

    @BeforeEach
    void setup(DockerRabbitMQ rabbitMQ) throws IOException, TimeoutException, URISyntaxException, InterruptedException {
        rabbitMQ.container().execInContainer("rabbitmqctl", "add_vhost", TWP_VHOST);
        rabbitMQ.container().execInContainer("rabbitmqctl", "set_permissions", "-p", TWP_VHOST, "guest", ".*", ".*", ".*");

        ConnectionFactory connectionFactory = rabbitMQ.connectionFactory();
        connectionFactory.setNetworkRecoveryInterval(1000);
        connectionFactory.setVirtualHost(TWP_VHOST);
        connection = connectionFactory.newConnection();
        channel = connection.createChannel();
        testee = new SaaSSubscriptionDeadLetterQueueHealthCheck(rabbitMQ.getConfigurationBuilder()
            .vhost(Optional.of(TWP_VHOST))
            .build());
    }

    @AfterEach
    void tearDown(DockerRabbitMQ rabbitMQ) throws Exception {
        closeQuietly(connection, channel);
        rabbitMQ.reset();
    }

    @Test
    void healthCheckShouldReturnUnhealthyWhenRabbitMQIsDown() throws Exception {
        rabbitMQExtension.getRabbitMQ().stopApp();

        assertThat(testee.check().block().isUnHealthy()).isTrue();
    }

    @Test
    void healthCheckShouldReturnHealthyWhenSaaSSubscriptionDeadLetterQueuesAreEmpty() throws Exception {
        createDeadLetterQueues(ImmutableMap.of(
            SAAS_SUBSCRIPTION_DEAD_LETTER_QUEUE, ROUTING_KEY_USER,
            SAAS_DOMAIN_SUBSCRIPTION_DEAD_LETTER_QUEUE, ROUTING_KEY_DOMAIN));

        assertThat(testee.check().block().isHealthy()).isTrue();
    }

    @Test
    void healthCheckShouldReturnUnhealthyWhenThereIsNoDeadLetterQueue() {
        assertThat(testee.check().block().isUnHealthy()).isTrue();
    }

    @Test
    void healthCheckShouldReturnUnhealthyWhenOnlySubscriptionDeadLetterQueueIsDeclared() throws Exception {
        createDeadLetterQueues(ImmutableMap.of(
            SAAS_SUBSCRIPTION_DEAD_LETTER_QUEUE, ROUTING_KEY_USER));
        assertThat(testee.check().block().isUnHealthy()).isTrue();
    }

    @Test
    void healthCheckShouldReturnUnhealthyWhenOnlyDomainSubscriptionDeadLetterQueueIsDeclared() throws Exception {
        createDeadLetterQueues(ImmutableMap.of(
            SAAS_DOMAIN_SUBSCRIPTION_DEAD_LETTER_QUEUE, ROUTING_KEY_DOMAIN));
        assertThat(testee.check().block().isUnHealthy()).isTrue();
    }

    @Test
    void healthCheckShouldReturnDegradedWhenSaaSSubscriptionDeadLetterQueueIsNotEmpty() throws Exception {
        createDeadLetterQueues(ImmutableMap.of(
            SAAS_SUBSCRIPTION_DEAD_LETTER_QUEUE, ROUTING_KEY_USER,
            SAAS_DOMAIN_SUBSCRIPTION_DEAD_LETTER_QUEUE, ROUTING_KEY_DOMAIN));
        publishAMessage(ROUTING_KEY_USER);

        awaitAtMostOneMinute.until(() -> testee.check().block().isDegraded());
    }

    @Test
    void healthCheckShouldReturnDegradedWhenSaaSDomainSubscriptionDeadLetterQueueIsNotEmpty() throws Exception {
        createDeadLetterQueues(ImmutableMap.of(
            SAAS_SUBSCRIPTION_DEAD_LETTER_QUEUE, ROUTING_KEY_USER,
            SAAS_DOMAIN_SUBSCRIPTION_DEAD_LETTER_QUEUE, ROUTING_KEY_DOMAIN));
        publishAMessage(ROUTING_KEY_DOMAIN);

        awaitAtMostOneMinute.until(() -> testee.check().block().isDegraded());
    }

    private void createDeadLetterQueues(Map<String, String> deadLetterQueueRouting) throws IOException {
        channel.exchangeDeclare(TWP_SAAS_SUBSCRIPTION_EXCHANGE_DEFAULT, DIRECT, DURABLE);
        deadLetterQueueRouting.forEach(this::createDeadLetterQueue);
    }

    private void createDeadLetterQueue(String deadLetterQueue, String routingKey) {
        try {
            channel.queueDeclare(deadLetterQueue, DURABLE, !EXCLUSIVE, AUTO_DELETE, NO_QUEUE_DECLARE_ARGUMENTS).getQueue();
            channel.queueBind(deadLetterQueue, TWP_SAAS_SUBSCRIPTION_EXCHANGE_DEFAULT, routingKey);
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    private void publishAMessage(String routingKey) throws IOException {
        AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder()
            .deliveryMode(PERSISTENT_TEXT_PLAIN.getDeliveryMode())
            .priority(PERSISTENT_TEXT_PLAIN.getPriority())
            .contentType(PERSISTENT_TEXT_PLAIN.getContentType())
            .build();

        channel.basicPublish(TWP_SAAS_SUBSCRIPTION_EXCHANGE_DEFAULT, routingKey, basicProperties, "Hello, world!".getBytes(StandardCharsets.UTF_8));
    }

    private void closeQuietly(AutoCloseable... closeables) {
        Arrays.stream(closeables).forEach(this::closeQuietly);
    }

    private void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            //ignore error
        }
    }
}
