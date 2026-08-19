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

package com.linagora.tmail.james.jmap;

import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.lifecycle.api.Startable;

import com.linagora.tmail.james.jmap.contact.ContactMessageHandlerResult;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactMessageHandler;
import com.linagora.tmail.james.jmap.contact.Failure;
import com.linagora.tmail.james.jmap.json.EmailAddressContactMessageSerializer;
import com.linagora.tmail.rabbitmq.ManagedRabbitMQConsumer;
import com.linagora.tmail.rabbitmq.QueueDeclaration;
import com.rabbitmq.client.BuiltinExchangeType;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.Sender;

public class RabbitMQEmailAddressContactSubscriber implements Startable, Closeable {

    private final ManagedRabbitMQConsumer consumer;
    private final EmailAddressContactMessageHandler messageHandler;

    @Inject
    public RabbitMQEmailAddressContactSubscriber(@Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) ReceiverProvider receiverProvider,
                                                 @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) Sender sender,
                                                 RabbitMQEmailAddressContactConfiguration configuration,
                                                 EmailAddressContactMessageHandler messageHandler,
                                                 RabbitMQConfiguration commonRabbitMQConfiguration) {
        this.messageHandler = messageHandler;
        this.consumer = new ManagedRabbitMQConsumer.Factory(sender, receiverProvider)
            .create(ManagedRabbitMQConsumer.Parameters.builder()
                .queueDeclaration(QueueDeclaration.builder()
                    .binding(configuration.getExchangeName(), BuiltinExchangeType.DIRECT, EMPTY_ROUTING_KEY)
                    .queue(configuration.queueName())
                    .deadLetterExchange(configuration.getDeadLetterExchange(), BuiltinExchangeType.DIRECT)
                    .deadLetterQueue(configuration.getDeadLetterQueue())
                    .deadLetterRoutingKey(EMPTY_ROUTING_KEY)
                    .build())
                .queueArguments(commonRabbitMQConfiguration::workQueueArgumentsBuilder)
                .concurrency(DEFAULT_CONCURRENCY)
                .handleDelivery(this::messageConsume)
                .build());
    }

    public void start() {
        consumer.init();
    }

    private Mono<Void> messageConsume(AcknowledgableDelivery ackDelivery) {
        return Mono.fromCallable(() -> new String(ackDelivery.getBody(), StandardCharsets.UTF_8))
            .map(EmailAddressContactMessageSerializer::deserializeEmailAddressContactMessageAsJava)
            .flatMap(message -> Mono.from(messageHandler.handler(message)))
            .flatMap(this::failOnHandlerFailure);
    }

    private Mono<Void> failOnHandlerFailure(ContactMessageHandlerResult handlerResult) {
        if (handlerResult instanceof Failure failure) {
            return Mono.error(failure.error());
        }
        return Mono.empty();
    }

    @PreDestroy
    @Override
    public void close() {
        consumer.close();
    }
}
