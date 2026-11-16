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

package com.linagora.tmail.space;

import static org.apache.james.backends.rabbitmq.RabbitMQExtension.IsolationPolicy.WEAK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.james.backends.rabbitmq.DockerRabbitMQ;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQDeadLetterQueuesHealthCheck;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.core.healthcheck.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableSet;
import com.rabbitmq.client.Channel;

class SpaceDeadLetterQueueHealthCheckTest {
    // Twake Space on a vhost of its own, TMail on the default one: rabbitmq.uri points elsewhere than rabbitmq.properties
    private static final String SPACE_VHOST = "twake-space";

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(WEAK);

    private SpaceConfiguration configuration;
    private SpacePublisher publisher;

    @BeforeEach
    void setUp() throws Exception {
        DockerRabbitMQ rabbitMQ = rabbitMQExtension.getRabbitMQ();
        rabbitMQ.container().execInContainer("rabbitmqctl", "add_vhost", SPACE_VHOST);
        rabbitMQ.container().execInContainer("rabbitmqctl", "set_permissions", "-p", SPACE_VHOST, rabbitMQ.getUsername(), ".*", ".*", ".*");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (publisher != null) {
            try (Channel channel = channel()) {
                channel.queueDelete(configuration.deadLetterQueue());
            }
            publisher.close();
        }
    }

    @Test
    void deadLetterQueueShouldBeCheckedOnTheVhostOfRabbitMQUri() throws Exception {
        declareDeadLetterQueue(Optional.of(spaceVhostUri()));

        Result result = healthCheck().check().block();

        assertThat(result.isHealthy()).describedAs("%s", result.getCause()).isTrue();
    }

    @Test
    void deadLetterQueueShouldBeDegradedWhenItHoldsAMessageOnTheVhostOfRabbitMQUri() throws Exception {
        declareDeadLetterQueue(Optional.of(spaceVhostUri()));
        try (Channel channel = channel()) {
            channel.basicPublish("", configuration.deadLetterQueue(), null, "{}".getBytes(StandardCharsets.UTF_8));
        }

        // The management API refreshes the queue lengths every few seconds
        await().atMost(Duration.ofMinutes(1)).untilAsserted(() -> {
            Result result = healthCheck().check().block();

            assertThat(result.isDegraded()).describedAs("%s", result.getCause()).isTrue();
            assertThat(result.getCause()).hasValueSatisfying(cause -> assertThat(cause).contains(configuration.deadLetterQueue()));
        });
    }

    @Test
    void deadLetterQueueShouldBeCheckedOnTheTMailServerWithoutRabbitMQUri() throws Exception {
        declareDeadLetterQueue(Optional.empty());

        Result result = healthCheck().check().block();

        assertThat(result.isHealthy()).describedAs("%s", result.getCause()).isTrue();
    }

    // Declared through the connection of the extension, as its consumer does
    private void declareDeadLetterQueue(Optional<URI> rabbitMQUri) throws Exception {
        BaseConfiguration properties = new BaseConfiguration();
        rabbitMQUri.ifPresent(uri -> properties.addProperty("rabbitmq.uri", uri.toString()));
        properties.addProperty("deadLetterQueue", "tmail-space-dead-letter-" + UUID.randomUUID());
        configuration = SpaceConfiguration.from(properties);
        publisher = SpacePublisher.create(configuration, tmailRabbitMQConfiguration(), Clock.systemUTC());

        try (Channel channel = channel()) {
            channel.queueDeclare(configuration.deadLetterQueue(), true, false, false, null);
        }
    }

    private Channel channel() throws Exception {
        return publisher.connectionPool().getResilientConnection().block().createChannel();
    }

    private RabbitMQDeadLetterQueuesHealthCheck healthCheck() throws Exception {
        return new RabbitMQDeadLetterQueuesHealthCheck(ImmutableSet.of(
            new SpaceModule().spaceDeadLetterQueue(tmailRabbitMQConfiguration(), configuration)));
    }

    private RabbitMQConfiguration tmailRabbitMQConfiguration() throws Exception {
        return rabbitMQExtension.getRabbitMQ().getConfiguration();
    }

    private URI spaceVhostUri() throws Exception {
        DockerRabbitMQ rabbitMQ = rabbitMQExtension.getRabbitMQ();
        URI server = rabbitMQ.amqpUri();
        return new URI("amqp", rabbitMQ.getUsername() + ":" + rabbitMQ.getPassword(), server.getHost(), server.getPort(),
            "/" + SPACE_VHOST, null, null);
    }
}
