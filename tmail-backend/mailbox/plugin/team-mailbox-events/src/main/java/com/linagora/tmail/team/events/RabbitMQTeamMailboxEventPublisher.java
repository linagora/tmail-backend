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

package com.linagora.tmail.team.events;

import static com.rabbitmq.client.MessageProperties.PERSISTENT_TEXT_PLAIN;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

public class RabbitMQTeamMailboxEventPublisher {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CONTENT_TYPE = "application/json";

    public static RabbitMQTeamMailboxEventPublisher create(TeamMailboxEventsConfiguration configuration, RabbitMQConfiguration tmailRabbitMQConfiguration) {
        RabbitMQConfiguration rabbitMQConfiguration = configuration.amqpUri()
            .map(amqpUri -> amqpUri.toRabbitMqConfiguration(tmailRabbitMQConfiguration).build())
            .orElse(tmailRabbitMQConfiguration);
        SimpleConnectionPool connectionPool = new SimpleConnectionPool(new RabbitMQConnectionFactory(rabbitMQConfiguration),
            SimpleConnectionPool.Configuration.DEFAULT);
        return new RabbitMQTeamMailboxEventPublisher(connectionPool, configuration);
    }

    private final SimpleConnectionPool connectionPool;
    private final Sender sender;
    private final TeamMailboxEventsConfiguration configuration;
    private final Mono<AMQP.Exchange.DeclareOk> exchangeDeclaration;

    private RabbitMQTeamMailboxEventPublisher(SimpleConnectionPool connectionPool, TeamMailboxEventsConfiguration configuration) {
        this.connectionPool = connectionPool;
        this.sender = RabbitFlux.createSender(new SenderOptions().connectionMono(connectionPool.getResilientConnection()));
        this.configuration = configuration;
        this.exchangeDeclaration = sender.declareExchange(ExchangeSpecification.exchange(configuration.exchange())
                .type(BuiltinExchangeType.TOPIC.getType())
                .durable(true))
            .cacheInvalidateIf(declareOk -> false);
    }

    public Mono<Void> publish(Flux<TeamMailboxEvent> events) {
        return exchangeDeclaration
            .thenMany(sender.sendWithPublishConfirms(events.concatMap(event -> Mono.fromCallable(() -> toMessage(event)))))
            .handle((result, sink) -> {
                if (!result.isAck()) {
                    sink.error(new RuntimeException("Team mailbox event " + result.getOutboundMessage().getProperties().getMessageId() + " was not acked by RabbitMQ"));
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    public void close() {
        sender.close();
        connectionPool.close();
    }

    private OutboundMessage toMessage(TeamMailboxEvent event) throws JsonProcessingException {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
            .messageId(event.messageId().serialize() + ":" + event.direction().asString())
            .contentType(CONTENT_TYPE)
            .deliveryMode(PERSISTENT_TEXT_PLAIN.getDeliveryMode())
            .build();
        return new OutboundMessage(configuration.exchange(), configuration.routingKey(event.direction()), properties,
            OBJECT_MAPPER.writeValueAsBytes(toJson(event)));
    }

    private ObjectNode toJson(TeamMailboxEvent event) {
        ObjectNode json = OBJECT_MAPPER.createObjectNode()
            .put("teamMailbox", event.teamMailbox().asString())
            .put("domain", event.teamMailbox().domain().asString())
            .put("direction", event.direction().asString())
            .put("mailboxPath", event.mailboxPath().getName())
            .put("mailboxId", event.mailboxId().serialize())
            .put("messageId", event.messageId().serialize())
            .put("subject", event.subject().orElse(null));
        json.set("from", toJson(event.from()));
        json.set("to", toJson(event.to()));
        json.set("cc", toJson(event.cc()));
        json.set("bcc", toJson(event.bcc()));
        return json
            .put("date", event.date().map(Instant::toString).orElse(null))
            .put("timestamp", event.timestamp().toString());
    }

    private JsonNode toJson(Optional<List<TeamMailboxEvent.EmailAddress>> addresses) {
        return addresses.<JsonNode>map(list -> {
            ArrayNode array = OBJECT_MAPPER.createArrayNode();
            list.forEach(address -> array.addObject()
                .put("name", address.name().orElse(null))
                .put("email", address.email()));
            return array;
        }).orElse(NullNode.getInstance());
    }
}
