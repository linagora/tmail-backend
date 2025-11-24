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

package org.apache.james.events;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import org.apache.james.events.RoutingKeyConverter.RoutingKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import io.lettuce.core.RedisException;
import io.lettuce.core.api.reactive.RedisSetReactiveCommands;
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RedisKeyEventDispatcher {

    public static final Predicate<? super Throwable> REDIS_ERROR_PREDICATE =
        throwable -> throwable instanceof RedisException || throwable instanceof TimeoutException;

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisKeyEventDispatcher.class);

    private final EventSerializer eventSerializer;
    private final RedisPubSubReactiveCommands<String, String> redisPublisher;
    private final RedisSetReactiveCommands<String, String> redisSetReactiveCommands;
    private final EventBusId eventBusId;
    private final RedisEventBusConfiguration redisEventBusConfiguration;

    public RedisKeyEventDispatcher(EventBusId eventBusId,
                                   EventSerializer eventSerializer,
                                   RedisPubSubReactiveCommands<String, String> redisPublisher,
                                   RedisSetReactiveCommands<String, String> redisSetReactiveCommands,
                                   RedisEventBusConfiguration redisEventBusConfiguration) {
        this.eventSerializer = eventSerializer;
        this.redisPublisher = redisPublisher;
        this.redisSetReactiveCommands = redisSetReactiveCommands;
        this.eventBusId = eventBusId;
        this.redisEventBusConfiguration = redisEventBusConfiguration;
    }

    public Mono<Void> dispatch(Event event, Set<RegistrationKey> keys) {
        return dispatchToRemoteListeners(event, keys)
            .doOnError(err -> LOGGER.error("Error dispatching Redis key event", err))
            .then();
    }

    public Mono<Void> dispatch(Collection<EventBus.EventWithRegistrationKey> events) {
        return dispatchToRemoteListeners(events)
            .doOnError(err -> LOGGER.error("Error dispatching Redis key event batch", err))
            .then();
    }

    private Mono<Void> dispatchToRemoteListeners(Event event, Set<RegistrationKey> keys) {
        return Mono.fromCallable(() -> eventSerializer.toJson(event))
            .flatMap(serializedJson -> remoteKeysDispatch(serializedJson, keys))
            .then();
    }

    private Mono<Void> dispatchToRemoteListeners(Collection<EventBus.EventWithRegistrationKey> events) {
        ImmutableList<Event> underlyingEvents = events.stream()
            .map(EventBus.EventWithRegistrationKey::event)
            .collect(ImmutableList.toImmutableList());

        ImmutableSet<RegistrationKey> keys = events.stream()
            .flatMap(e -> e.keys().stream())
            .collect(ImmutableSet.toImmutableSet());

        return Mono.fromCallable(() -> eventSerializer.toJson(underlyingEvents))
            .flatMap(serializedJson -> remoteKeysDispatch(serializedJson, keys))
            .then();
    }

    public Mono<Void> remoteKeysDispatch(String eventAsJson, Set<RegistrationKey> keys) {
        return remoteDispatch(eventAsJson, keys.stream().map(RoutingKey::of).collect(ImmutableList.toImmutableList()));
    }

    private Mono<Void> remoteDispatch(String eventAsJson, Collection<RoutingKey> routingKeys) {
        if (routingKeys.isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(routingKeys)
            .flatMap(routingKey ->
                getTargetChannels(routingKey)
                    .flatMap(channel -> redisPublisher.publish(channel, KeyChannelMessage.from(eventBusId, routingKey, eventAsJson).serialize()))
                    .timeout(redisEventBusConfiguration.durationTimeout())
                    .onErrorResume(REDIS_ERROR_PREDICATE.and(e -> redisEventBusConfiguration.failureIgnore()),
                        e -> {
                            LOGGER.warn("Redis dispatch failed for routingKey={}", routingKey, e);
                            return Flux.empty();
                        })
                    .then())
            .then();
    }

    private Flux<String> getTargetChannels(RoutingKey routingKey) {
        return redisSetReactiveCommands.smembers(routingKey.asString());
    }
}