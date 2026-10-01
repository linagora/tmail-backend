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

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.IOException;
import java.time.Duration;

import org.apache.james.backends.rabbitmq.QueueArguments;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.core.Username;
import org.apache.james.webadmin.service.DeleteUserDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linagora.tmail.rabbitmq.ManagedRabbitMQConsumer;
import com.linagora.tmail.rabbitmq.QueueDeclaration;
import com.linagora.tmail.saas.rabbitmq.TWPCommonRabbitMQConfiguration;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.AcknowledgableDelivery;

public final class TWPUserDeletionConsumer {
    public record UserDeletionConsumerConfig(String queue, String deadLetterQueue) {
        public static final UserDeletionConsumerConfig DEFAULT = new UserDeletionConsumerConfig("tmail-user-deletion", "tmail-user-deletion-dead-letter");
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(TWPUserDeletionConsumer.class);
    private static final Duration CONSUMER_TIMEOUT = Duration.ofMinutes(10L);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TWPUserDeletionConsumer() {
    }

    public static ManagedRabbitMQConsumer create(ReactorRabbitMQChannelPool channelPool,
                                                 RabbitMQConfiguration rabbitMQConfiguration,
                                                 TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration,
                                                 TWPUserDeletionRabbitMQConfiguration userDeletionRabbitMQConfiguration,
                                                 UserDeletionConsumerConfig consumerConfig,
                                                 DeleteUserDataService deleteUserDataService) {
        return new ManagedRabbitMQConsumer.Factory(channelPool)
            .create(ManagedRabbitMQConsumer.Parameters.builder()
                .queueDeclaration(QueueDeclaration.builder()
                    .binding(userDeletionRabbitMQConfiguration.b2cExchange(), userDeletionRabbitMQConfiguration.b2cRoutingKey())
                    .binding(userDeletionRabbitMQConfiguration.b2bExchange(), userDeletionRabbitMQConfiguration.b2bRoutingKey())
                    .queue(consumerConfig.queue())
                    .deadLetterQueue(consumerConfig.deadLetterQueue())
                    .build())
                .queueArguments(() -> queueArgumentSupplier(rabbitMQConfiguration, twpCommonRabbitMQConfiguration))
                .singleActiveConsumer()
                .consumerTimeout(CONSUMER_TIMEOUT)
                .qos(DEFAULT_CONCURRENCY)
                .handleDelivery(delivery -> deleteUserData(delivery, deleteUserDataService))
                .build());
    }

    private static QueueArguments.Builder queueArgumentSupplier(RabbitMQConfiguration rabbitMQConfiguration,
                                                                TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration) {
        if (!twpCommonRabbitMQConfiguration.quorumQueuesBypass()) {
            return rabbitMQConfiguration.workQueueArgumentsBuilder();
        }
        return QueueArguments.builder();
    }

    private static Mono<Void> deleteUserData(AcknowledgableDelivery ackDelivery, DeleteUserDataService deleteUserDataService) {
        return Mono.fromCallable(() -> parseUsername(ackDelivery.getBody()))
            .doOnNext(username -> LOGGER.info("Deleting data of user {} following a TWP deletion event", username.asString()))
            .flatMap(username -> deleteUserDataService.performer().deleteUserData(username)
                .doOnSuccess(any -> LOGGER.info("Deleted data of user {}", username.asString())));
    }

    private static Username parseUsername(byte[] body) throws IOException {
        JsonNode internalEmail = OBJECT_MAPPER.readTree(body).required("internalEmail");
        if (!internalEmail.isTextual() || internalEmail.asText().isBlank()) {
            throw new IllegalArgumentException("'internalEmail' must be a non-blank string");
        }
        return Username.of(internalEmail.asText());
    }
}
