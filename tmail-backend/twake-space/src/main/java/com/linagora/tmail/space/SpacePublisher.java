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

import static com.rabbitmq.client.MessageProperties.PERSISTENT_TEXT_PLAIN;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

/**
 * The RabbitMQ side of the space extension: the activity events TwakeSpace reads, and the sync requests ldap-rest
 * answers. Event ids are deterministic, so a republish keeps the id of the first copy.
 */
public class SpacePublisher {
    public enum Direction {
        RECEIVED("received"),
        SENT("sent");

        private final String value;

        Direction(String value) {
            this.value = value;
        }

        public String asString() {
            return value;
        }

        String eventType() {
            return "com.twake.mail.message." + value + ".v1";
        }
    }

    public static final String PROVISIONED = "com.twake.mail.space.provisioned.v1";
    public static final String SYNC_REQUESTED = "twake.space.sync.requested";
    public static final String NO_SUBJECT = "(no subject)";
    private static final String SOURCE = "twake://mail";
    private static final String CONTENT_TYPE = "application/json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static SpacePublisher create(SpaceConfiguration configuration, RabbitMQConfiguration tmailRabbitMQConfiguration, Clock clock) {
        RabbitMQConfiguration rabbitMQConfiguration = configuration.rabbitMQUri()
            .map(amqpUri -> amqpUri.toRabbitMqConfiguration(tmailRabbitMQConfiguration).build())
            .orElse(tmailRabbitMQConfiguration);
        SimpleConnectionPool connectionPool = new SimpleConnectionPool(new RabbitMQConnectionFactory(rabbitMQConfiguration),
            SimpleConnectionPool.Configuration.DEFAULT);
        return new SpacePublisher(connectionPool, configuration, clock);
    }

    private final SimpleConnectionPool connectionPool;
    private final Sender sender;
    private final SpaceConfiguration configuration;
    private final Clock clock;
    private final ConcurrentHashMap<String, Mono<AMQP.Exchange.DeclareOk>> exchangeDeclarations = new ConcurrentHashMap<>();

    private SpacePublisher(SimpleConnectionPool connectionPool, SpaceConfiguration configuration, Clock clock) {
        this.connectionPool = connectionPool;
        this.sender = RabbitFlux.createSender(new SenderOptions().connectionMono(connectionPool.getResilientConnection()));
        this.configuration = configuration;
        this.clock = clock;
    }

    public SimpleConnectionPool connectionPool() {
        return connectionPool;
    }

    public Sender sender() {
        return sender;
    }

    public ReceiverProvider receiverProvider() {
        return () -> RabbitFlux.createReceiver(new ReceiverOptions().connectionMono(connectionPool.getResilientConnection()));
    }

    public Mono<Void> publishProvisioned(String organizationId, String spaceId, MailboxId rootId) {
        ObjectNode data = OBJECT_MAPPER.createObjectNode().put("space_id", spaceId);
        data.putObject("resource")
            .put("kind", "mailbox")
            .put("id", rootId.serialize());
        String id = spaceId + ":" + rootId.serialize() + ":provisioned";
        return publish(configuration.activityExchange(), PROVISIONED, id, cloudEvent(id, PROVISIONED, clock.instant(), organizationId, data));
    }

    public Mono<Void> publishMessage(String organizationId, MailboxId rootId, MessageId messageId, Direction direction,
                                     Optional<String> subject, Instant time) {
        ObjectNode data = OBJECT_MAPPER.createObjectNode();
        ObjectNode object = data.putObject("object")
            .put("type", "message")
            .put("id", messageId.serialize())
            .put("title", subject.filter(value -> !value.isBlank()).orElse(NO_SUBJECT));
        object.putObject("container")
            .put("kind", "mailbox")
            .put("id", rootId.serialize());
        String id = rootId.serialize() + ":" + messageId.serialize() + ":" + direction.asString();
        String type = direction.eventType();
        return publish(configuration.activityExchange(), type, id, cloudEvent(id, type, time, organizationId, data));
    }

    /** Asks ldap-rest for a snapshot: of every space, of an organization's, or of one space. */
    public Mono<Void> requestSync(Optional<String> organizationId, Optional<String> spaceId) {
        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        organizationId.ifPresent(value -> body.put("organizationId", value));
        spaceId.ifPresent(value -> body.put("id", value));
        body.put("timestamp", clock.instant().toString());
        return publish(configuration.spaceExchange(), SYNC_REQUESTED, UUID.randomUUID().toString(), body);
    }

    private ObjectNode cloudEvent(String id, String type, Instant time, String organizationId, ObjectNode data) {
        ObjectNode event = OBJECT_MAPPER.createObjectNode()
            .put("specversion", "1.0")
            .put("id", id)
            .put("source", SOURCE)
            .put("type", type)
            .put("time", time.toString())
            .put("twakeorg", organizationId);
        event.set("data", data);
        return event;
    }

    private Mono<Void> publish(String exchange, String routingKey, String messageId, ObjectNode body) {
        return exchangeDeclaration(exchange)
            .then(Mono.fromCallable(() -> toMessage(exchange, routingKey, messageId, body)))
            .flatMap(message -> sender.sendWithPublishConfirms(Mono.just(message))
                .next()
                .handle((result, sink) -> {
                    if (!result.isAck()) {
                        sink.error(new RuntimeException("Space event " + messageId + " was not acked by RabbitMQ"));
                        return;
                    }
                    sink.complete();
                }))
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    private Mono<AMQP.Exchange.DeclareOk> exchangeDeclaration(String exchange) {
        return exchangeDeclarations.computeIfAbsent(exchange, name -> sender.declareExchange(ExchangeSpecification.exchange(name)
                .type(BuiltinExchangeType.TOPIC.getType())
                .durable(true))
            .cacheInvalidateIf(declareOk -> false));
    }

    private OutboundMessage toMessage(String exchange, String routingKey, String messageId, ObjectNode body) throws JsonProcessingException {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
            .messageId(messageId)
            .contentType(CONTENT_TYPE)
            .deliveryMode(PERSISTENT_TEXT_PLAIN.getDeliveryMode())
            .build();
        return new OutboundMessage(exchange, routingKey, properties, OBJECT_MAPPER.writeValueAsBytes(body));
    }

    @PreDestroy
    public void close() {
        sender.close();
        connectionPool.close();
    }
}
