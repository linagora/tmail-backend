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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.Sender;

public class AggregatedTmailGroupEventDispatcher implements TmailGroupEventDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(AggregatedTmailGroupEventDispatcher.class);

    private final ImmutableList<DefaultTmailGroupEventDispatcher> delegates;
    private final EventDeadLetters deadLetters;
    private final RabbitMQConfiguration configuration;
    private final DispatchingFailureGroup dispatchingFailureGroup;

    public AggregatedTmailGroupEventDispatcher(List<NamingStrategy> namingStrategies, EventBusId eventBusId,
                                               Sender sender, EventDeadLetters deadLetters,
                                               RabbitMQConfiguration configuration) {
        Preconditions.checkArgument(!namingStrategies.isEmpty(), "At least one naming strategy is required");

        this.delegates = namingStrategies.stream()
            .map(namingStrategy -> new DefaultTmailGroupEventDispatcher(namingStrategy, eventBusId, sender, configuration))
            .collect(ImmutableList.toImmutableList());
        this.deadLetters = deadLetters;
        this.configuration = configuration;
        this.dispatchingFailureGroup = new DispatchingFailureGroup(namingStrategies.getFirst().getEventBusName());
    }

    @Override
    public void start() {
        Flux.fromIterable(delegates)
            .concatMap(DefaultTmailGroupEventDispatcher::start)
            .then()
            .block();
    }

    @Override
    public Mono<Void> dispatch(byte[] serializedEvent, Event event) {
        return randomDelegate().dispatch(serializedEvent)
            .doOnError(ex -> LOGGER.error(
                "cannot dispatch event of type '{}' belonging '{}' with id '{}' to remote groups, store it into dead letter",
                event.getClass().getSimpleName(),
                event.getUsername().asString(),
                event.getEventId().getId(),
                ex))
            .onErrorResume(ex -> deadLetters.store(dispatchingFailureGroup, event)
                .then(propagateErrorIfNeeded(ex)));
    }

    @Override
    public Mono<Void> dispatch(byte[] serializedEvent, List<Event> events) {
        return randomDelegate().dispatch(serializedEvent)
            .onErrorResume(ex -> Flux.fromIterable(events)
                .concatMap(event -> {
                    LOGGER.error(
                        "cannot dispatch event of type '{}' belonging '{}' with id '{}' to remote groups, store it into dead letter",
                        event.getClass().getSimpleName(),
                        event.getUsername().asString(),
                        event.getEventId().getId(),
                        ex);
                    return deadLetters.store(dispatchingFailureGroup, event);
                })
                .then(propagateErrorIfNeeded(ex)));
    }

    private Mono<Void> propagateErrorIfNeeded(Throwable throwable) {
        if (configuration.eventBusPropagateDispatchError()) {
            return Mono.error(throwable);
        }
        return Mono.empty();
    }

    private DefaultTmailGroupEventDispatcher randomDelegate() {
        return delegates.get(ThreadLocalRandom.current().nextInt(delegates.size()));
    }
}
