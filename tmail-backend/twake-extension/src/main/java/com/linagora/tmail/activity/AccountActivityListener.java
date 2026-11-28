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


package com.linagora.tmail.activity;

import static com.rabbitmq.client.MessageProperties.PERSISTENT_TEXT_PLAIN;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.reactivestreams.Publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;

/**
 * Publishes the mail activity of an account on a RabbitMQ topic exchange, for the Twake Workplace components that
 * track it. Declared in {@code listeners.xml}, it needs TMail to run with RabbitMQ:
 *
 * <pre>{@code
 * <listener>
 *   <class>com.linagora.tmail.activity.AccountActivityListener</class>
 *   <configuration>
 *     <exchange>activity</exchange>
 *   </configuration>
 * </listener>
 * }</pre>
 *
 * Each message delivered into a personal mailbox is reported as a {@code com.twake.mail.email.received.v1} CloudEvent, routed
 * by its type, in the format of the calendar and contacts activity: {@code twakeactor} is the user whose
 * session added the message, {@code twakeresourceowner} the owner of the mailbox.
 */
public class AccountActivityListener implements EventListener.ReactiveGroupEventListener {
    public static class AccountActivityListenerGroup extends Group {

    }

    public record Configuration(String exchange) {
        private static final String EXCHANGE_PARAMETER = "exchange";
        public static final Configuration DEFAULT = new Configuration("activity");

        public static Configuration from(HierarchicalConfiguration<ImmutableNode> configuration) {
            return Optional.ofNullable(configuration.getString(EXCHANGE_PARAMETER, null))
                .map(Configuration::new)
                .orElse(DEFAULT);
        }

        public Configuration {
            Preconditions.checkArgument(!exchange.isBlank(), "'%s' must not be blank", EXCHANGE_PARAMETER);
        }
    }

    public enum ActivityType {
        EMAIL_RECEIVED("email", "received");

        private final String object;
        private final String action;

        ActivityType(String object, String action) {
            this.object = object;
            this.action = action;
        }

        public String asString() {
            return object + "." + action;
        }

        /** {@code com.twake.<app>.<object>.<action>.v1}, also the routing key, as ADR-067 defines it. */
        public String eventType() {
            return "com.twake." + APPLICATION + "." + asString() + ".v1";
        }
    }

    /** The activity of one message added by {@code addedEvent}, observed at {@code time}. */
    private record MessageActivity(MailboxEvents.Added addedEvent, MessageMetaData message, ActivityType type, Instant time) {
        MessageId messageId() {
            return message.getMessageId();
        }

        String id() {
            return AccountActivityListener.id(addedEvent.getMailboxId(), messageId(), type);
        }

        String subject() {
            return "mailboxes/" + addedEvent.getMailboxId().serialize() + "/" + messageId().serialize();
        }
    }

    public static final String APPLICATION = "mail";
    private static final Group GROUP = new AccountActivityListenerGroup();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CONTENT_TYPE = "application/cloudevents+json";
    private static final String SPEC_VERSION = "1.0";
    private static final String SOURCE = "twake://" + APPLICATION;
    // RabbitMQ confirms within milliseconds: a broker withholding its confirms fails the publication, as a NACK does,
    // so that the event bus retries it and then keeps it in the event dead letters.
    static final Duration PUBLICATION_TIMEOUT = Duration.ofSeconds(30);

    private final Sender sender;
    private final Clock clock;
    private final Configuration configuration;
    private final Mono<AMQP.Exchange.DeclareOk> exchangeDeclaration;
    private final Duration publicationTimeout;

    @Inject
    public AccountActivityListener(Sender sender, Clock clock, HierarchicalConfiguration<ImmutableNode> configuration) {
        this(sender, clock, Configuration.from(configuration));
    }

    public AccountActivityListener(Sender sender, Clock clock, Configuration configuration) {
        this(sender, clock, configuration, PUBLICATION_TIMEOUT);
    }

    AccountActivityListener(Sender sender, Clock clock, Configuration configuration, Duration publicationTimeout) {
        this.sender = sender;
        this.clock = clock;
        this.configuration = configuration;
        this.publicationTimeout = publicationTimeout;
        this.exchangeDeclaration = sender.declareExchange(ExchangeSpecification.exchange(configuration.exchange())
                .type(BuiltinExchangeType.TOPIC.getType())
                .durable(true))
            .cacheInvalidateIf(declareOk -> false);
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MailboxEvents.Added addedEvent
            && addedEvent.isDelivery()
            && addedEvent.getMailboxPath().getNamespace().equals(MailboxConstants.USER_NAMESPACE);
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof MailboxEvents.Added addedEvent && !addedEvent.getAdded().isEmpty()) {
            Instant time = clock.instant();
            Flux<OutboundMessage> messages = Flux.fromIterable(addedEvent.getAdded().values())
                .distinct(MessageMetaData::getMessageId)
                .concatMap(message -> Mono.fromCallable(() -> toMessage(new MessageActivity(addedEvent, message, ActivityType.EMAIL_RECEIVED, time))));
            return exchangeDeclaration
                .thenMany(sender.sendWithPublishConfirms(messages))
                .handle((result, sink) -> {
                    if (!result.isAck()) {
                        sink.error(new RuntimeException("Account activity " + result.getOutboundMessage().getProperties().getMessageId()
                            + " was not acked by RabbitMQ"));
                    }
                })
                .then()
                .timeout(publicationTimeout, Mono.error(() -> new RuntimeException("Account activity of " + addedEvent.getMailboxId().serialize()
                    + " was not confirmed by RabbitMQ within " + publicationTimeout)))
                .subscribeOn(Schedulers.boundedElastic());
        }
        return Mono.empty();
    }

    private OutboundMessage toMessage(MessageActivity activity) throws JsonProcessingException {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
            .messageId(activity.id())
            .contentType(CONTENT_TYPE)
            .deliveryMode(PERSISTENT_TEXT_PLAIN.getDeliveryMode())
            .build();
        return new OutboundMessage(configuration.exchange(), activity.type().eventType(), properties,
            OBJECT_MAPPER.writeValueAsBytes(toJson(activity)));
    }

    /** Name based, so the event of a message keeps its id when replayed from the event dead letters. */
    static String id(MailboxId mailboxId, MessageId messageId, ActivityType type) {
        return UUID.nameUUIDFromBytes((mailboxId.serialize() + ":" + messageId.serialize() + ":" + type.asString()).getBytes(UTF_8))
            .toString();
    }

    private ObjectNode toJson(MessageActivity activity) {
        MailboxEvents.Added addedEvent = activity.addedEvent();
        Username owner = addedEvent.getMailboxPath().getUser();
        ObjectNode json = OBJECT_MAPPER.createObjectNode()
            .put("specversion", SPEC_VERSION)
            .put("id", activity.id())
            .put("source", SOURCE)
            .put("type", activity.type().eventType())
            .put("time", activity.time().toString())
            .put("subject", activity.subject())
            .put("twakeactor", addedEvent.getUsername().asString())
            .put("twakeresourceowner", owner.asString())
            .put("twakedomain", owner.getDomainPart().map(Domain::asString).orElse(null));
        json.putObject("data")
            .put("mailboxPath", addedEvent.getMailboxPath().getName())
            .put("mailboxId", addedEvent.getMailboxId().serialize())
            .put("messageId", activity.messageId().serialize())
            .put("threadId", activity.message().getThreadId().serialize());
        return json;
    }
}
