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

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.core.Domain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.linagora.tmail.rabbitmq.ManagedRabbitMQConsumer;
import com.linagora.tmail.rabbitmq.QueueDeclaration;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.AcknowledgableDelivery;

/**
 * Reads the space events and the admin panel DNS event, and applies what each one carries to the team mailbox of the
 * space. Delivery is at least once: every handler is idempotent, so a redelivered event changes nothing.
 *
 * <p>The queue has a single active consumer handling one event at a time, so the events of a space are applied in
 * the order they were published.</p>
 */
public class SpaceEventsConsumer {
    public static final List<String> SPACE_ROUTING_KEYS = ImmutableList.of(
        "twake.space.created",
        "twake.space.synced",
        "twake.space.updated",
        "twake.space.deleted",
        "twake.space.member.added",
        "twake.space.member.removed",
        "twake.space.member.role.changed",
        "twake.space.sync.completed");

    static class MalformedEventException extends RuntimeException {
        MalformedEventException(String message) {
            super(message);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SpaceEventsConsumer.class);
    private static final Duration CONSUMER_TIMEOUT = Duration.ofMinutes(10L);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static ManagedRabbitMQConsumer create(SpacePublisher publisher, RabbitMQConfiguration rabbitMQConfiguration,
                                                 SpaceConfiguration configuration, SpaceEventsConsumer consumer) {
        QueueDeclaration.Builder queueDeclaration = QueueDeclaration.builder()
            .queue(configuration.queue())
            .deadLetterQueue(configuration.deadLetterQueue())
            .binding(configuration.dnsExchange(), configuration.dnsRoutingKey());
        SPACE_ROUTING_KEYS.forEach(routingKey -> queueDeclaration.binding(configuration.spaceExchange(), routingKey));

        return new ManagedRabbitMQConsumer.Factory(publisher.sender(), publisher.receiverProvider())
            .create(ManagedRabbitMQConsumer.Parameters.builder()
                .queueDeclaration(queueDeclaration.build())
                .queueArguments(rabbitMQConfiguration::workQueueArgumentsBuilder)
                .singleActiveConsumer()
                .consumerTimeout(CONSUMER_TIMEOUT)
                .qos(DEFAULT_CONCURRENCY)
                .handleDelivery(consumer::handle)
                .build());
    }

    private final SpacePublisher publisher;
    private final SpaceConfiguration configuration;
    private final Map<String, Function<JsonNode, Mono<Void>>> handlers;

    public SpaceEventsConsumer(SpaceReconciler reconciler, SpacePublisher publisher, SpaceConfiguration configuration) {
        this.publisher = publisher;
        this.configuration = configuration;
        this.handlers = ImmutableMap.<String, Function<JsonNode, Mono<Void>>>builder()
            .put("twake.space.created", json -> reconciler.apply(snapshot(json), false))
            .put("twake.space.synced", json -> reconciler.apply(snapshot(json), true))
            .put("twake.space.deleted", json -> reconciler.close(optionalDomain(json), text(json, "id")))
            .put("twake.space.sync.completed", json -> reconciler.closeUnlisted(text(json, "organizationId"), domain(json), spaceIds(json)))
            .put("twake.space.member.added", json -> reconciler.changeMembers(domain(json), text(json, "id"), members(json)))
            .put("twake.space.member.role.changed", json -> reconciler.changeMembers(domain(json), text(json, "id"), members(json)))
            .put("twake.space.member.removed", json -> reconciler.removeMembers(domain(json), text(json, "id"), removedMembers(json)))
            .put("twake.space.updated", this::renamed)
            .build();
    }

    public Mono<Void> handle(AcknowledgableDelivery delivery) {
        return handle(delivery.getEnvelope().getExchange(), delivery.getEnvelope().getRoutingKey(), delivery.getBody());
    }

    /** Fails, so that the event is dead lettered, when its body is not JSON or the handling failed. */
    public Mono<Void> handle(String exchange, String routingKey, byte[] body) {
        return Mono.fromCallable(() -> OBJECT_MAPPER.readTree(body))
            .flatMap(json -> Mono.defer(() -> route(exchange, routingKey).apply(json)))
            .onErrorResume(MalformedEventException.class, e -> {
                LOGGER.warn("Dropping malformed {} event: {}", routingKey, e.getMessage());
                return Mono.empty();
            });
    }

    private Function<JsonNode, Mono<Void>> route(String exchange, String routingKey) {
        if (exchange.equals(configuration.dnsExchange()) && routingKey.equals(configuration.dnsRoutingKey())) {
            return this::dnsValidated;
        }
        return handlers.getOrDefault(routingKey, json -> {
            LOGGER.debug("No handler for {} on {}", routingKey, exchange);
            return Mono.empty();
        });
    }

    private Mono<Void> renamed(JsonNode json) {
        // A team mailbox keeps its address when its space is renamed
        LOGGER.debug("Ignoring the rename of space {}", text(json, "id"));
        return Mono.empty();
    }

    private Mono<Void> dnsValidated(JsonNode json) {
        String organizationId = text(json, "organizationId");
        if (!json.path("mailDnsConfigurationValidated").asBoolean(false)) {
            return Mono.empty();
        }
        return publisher.requestSync(SpacePublisher.SyncScope.organization(organizationId));
    }

    private SpaceSnapshot snapshot(JsonNode json) {
        return new SpaceSnapshot(text(json, "organizationId"), domain(json), text(json, "id"), text(json, "name"), members(json));
    }

    private List<SpaceSnapshot.Member> members(JsonNode json) {
        JsonNode members = json.path("members");
        if (members.isMissingNode() || members.isNull()) {
            return ImmutableList.of();
        }
        if (!members.isArray()) {
            throw new MalformedEventException("'members' must be an array");
        }
        return StreamSupport.stream(members.spliterator(), false)
            .map(this::member)
            .collect(ImmutableList.toImmutableList());
    }

    private SpaceSnapshot.Member member(JsonNode json) {
        String role = text(json, "role");
        return new SpaceSnapshot.Member(json.path("email").asText(""),
            SpaceSnapshot.Role.parse(role).orElseThrow(() -> new MalformedEventException("Unknown member role " + role)));
    }

    /** A removed member may come without a role. */
    private List<SpaceSnapshot.Member> removedMembers(JsonNode json) {
        JsonNode members = json.path("members");
        if (!members.isArray()) {
            throw new MalformedEventException("'members' must be an array");
        }
        return StreamSupport.stream(members.spliterator(), false)
            .map(member -> new SpaceSnapshot.Member(member.path("email").asText(""), SpaceSnapshot.Role.VIEWER))
            .collect(ImmutableList.toImmutableList());
    }

    private List<String> spaceIds(JsonNode json) {
        JsonNode spaceIds = json.path("spaceIds");
        if (!spaceIds.isArray()) {
            throw new MalformedEventException("'spaceIds' must be an array");
        }
        return StreamSupport.stream(spaceIds.spliterator(), false)
            .map(JsonNode::asText)
            .collect(ImmutableList.toImmutableList());
    }

    private Domain domain(JsonNode json) {
        return optionalDomain(json)
            .orElseThrow(() -> new MalformedEventException("'organizationDomain' is missing, ldap-rest needs to know the organization's domain"));
    }

    private Optional<Domain> optionalDomain(JsonNode json) {
        return Optional.ofNullable(json.get("organizationDomain"))
            .filter(JsonNode::isTextual)
            .map(JsonNode::asText)
            .filter(value -> !value.isBlank())
            .map(value -> {
                try {
                    return Domain.of(value);
                } catch (IllegalArgumentException e) {
                    throw new MalformedEventException("Invalid 'organizationDomain' " + value);
                }
            });
    }

    private static String text(JsonNode json, String field) {
        return Optional.ofNullable(json.get(field))
            .filter(JsonNode::isTextual)
            .map(JsonNode::asText)
            .filter(value -> !value.isBlank())
            .orElseThrow(() -> new MalformedEventException("'" + field + "' must be a non-blank string"));
    }
}
