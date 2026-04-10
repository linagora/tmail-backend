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

import static org.apache.james.events.GroupRegistration.DEFAULT_RETRY_COUNT;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import io.lettuce.core.RedisException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class TMailEventDispatcher {
    public static final Predicate<? super Throwable> REDIS_ERROR_PREDICATE = throwable -> throwable instanceof RedisException || throwable instanceof TimeoutException;
    private static final Logger LOGGER = LoggerFactory.getLogger(TMailEventDispatcher.class);

    private final EventSerializer eventSerializer;
    private final TmailGroupRegistrationHandler groupRegistrationHandler;
    private final TmailGroupEventDispatcher groupEventDispatcher;
    private final RedisKeyEventDispatcher keyEventDispatcher;
    private final LocalKeyListenerExecutor localKeyListenerExecutor;

    TMailEventDispatcher(EventSerializer eventSerializer,
                         TmailGroupRegistrationHandler groupRegistrationHandler,
                         TmailGroupEventDispatcher groupEventDispatcher,
                         RedisKeyEventDispatcher keyEventDispatcher,
                         LocalKeyListenerExecutor localKeyListenerExecutor) {
        this.eventSerializer = eventSerializer;
        this.groupRegistrationHandler = groupRegistrationHandler;
        this.groupEventDispatcher = groupEventDispatcher;
        this.keyEventDispatcher = keyEventDispatcher;
        this.localKeyListenerExecutor = localKeyListenerExecutor;
    }

    void start() {
        groupEventDispatcher.start();
    }

    Mono<Void> dispatch(Event event, Set<RegistrationKey> keys) {
        return Flux
            .concat(
                executeLocalSynchronousListeners(ImmutableList.of(new EventBus.EventWithRegistrationKey(event, keys))),
                localKeyListenerExecutor.execute(event, keys),
                dispatchToRemoteListeners(event, keys))
            .doOnError(throwable -> LOGGER.error("error while dispatching event", throwable))
            .then();
    }

    Mono<Void> dispatch(Collection<EventBus.EventWithRegistrationKey> events) {
        return Flux
            .concat(
                executeLocalSynchronousListeners(events),
                localKeyListenerExecutor.execute(events),
                dispatchToRemoteListeners(events))
            .doOnError(throwable -> LOGGER.error("error while dispatching event", throwable))
            .then();
    }

    private Mono<Void> executeLocalSynchronousListeners(Collection<EventBus.EventWithRegistrationKey> events) {
        if (RabbitMQAndRedisEventBus.listenersToExecuteSynchronously.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromStream(groupRegistrationHandler.synchronousGroupRegistrations())
            .flatMap(registration -> registration.runListenerReliably(DEFAULT_RETRY_COUNT, events.stream()
                .map(EventBus.EventWithRegistrationKey::event)
                .collect(ImmutableList.toImmutableList())))
            .then();
    }

    private Mono<Void> dispatchToRemoteListeners(Collection<EventBus.EventWithRegistrationKey> events) {
        ImmutableList<Event> underlyingEvents = events.stream()
            .map(EventBus.EventWithRegistrationKey::event)
            .collect(ImmutableList.toImmutableList());

        ImmutableSet<RegistrationKey> keys = events.stream()
            .flatMap(event -> event.keys().stream())
            .collect(ImmutableSet.toImmutableSet());

        return Mono.fromCallable(() -> eventSerializer.toJson(underlyingEvents))
            .flatMap(serializedEvent -> Mono.zipDelayError(
                remoteGroupsDispatch(serializedEvent.jsonBytes(), underlyingEvents),
                keyEventDispatcher.remoteKeysDispatch(serializedEvent.json(), keys)))
            .then();
    }

    private Mono<Void> dispatchToRemoteListeners(Event event, Set<RegistrationKey> keys) {
        return Mono.fromCallable(() -> eventSerializer.toJson(event))
            .flatMap(serializedEvent -> Mono.zipDelayError(
                remoteGroupsDispatch(serializedEvent.jsonBytes(), event),
                keyEventDispatcher.remoteKeysDispatch(serializedEvent.json(), keys)))
            .then();
    }

    private Mono<Void> remoteGroupsDispatch(byte[] serializedEvent, Event event) {
        return groupEventDispatcher.dispatch(serializedEvent, event);
    }

    private Mono<Void> remoteGroupsDispatch(byte[] serializedEvent, List<Event> events) {
        return groupEventDispatcher.dispatch(serializedEvent, events);
    }
}
