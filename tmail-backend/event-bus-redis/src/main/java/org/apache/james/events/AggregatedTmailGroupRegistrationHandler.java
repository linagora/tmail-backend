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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.Sender;

public class AggregatedTmailGroupRegistrationHandler implements TmailGroupRegistrationHandler {
    private static List<TmailGroupRegistrationHandler> buildDelegates(List<NamingStrategy> namingStrategies,
                                                                      EventSerializer eventSerializer,
                                                                      ReactorRabbitMQChannelPool channelPool,
                                                                      Sender sender,
                                                                      ReceiverProvider receiverProvider,
                                                                      EventDeadLetters eventDeadLetters,
                                                                      ListenerExecutor listenerExecutor,
                                                                      RabbitMQEventBus.Configurations configurations) {
        Preconditions.checkArgument(!namingStrategies.isEmpty(), "At least one naming strategy is required");

        RabbitMQEventBus.Configurations dividedConcurrencyConfiguration = splitConcurrencyConfiguration(configurations, namingStrategies.size());

        return namingStrategies.stream()
            .map(namingStrategy -> new DefaultTmailGroupRegistrationHandler(namingStrategy, eventSerializer, channelPool,
                sender, receiverProvider, eventDeadLetters, listenerExecutor, dividedConcurrencyConfiguration))
            .collect(ImmutableList.toImmutableList());
    }

    @VisibleForTesting
    static RabbitMQEventBus.Configurations splitConcurrencyConfiguration(RabbitMQEventBus.Configurations configurations, int partitionCount) {
        Preconditions.checkArgument(partitionCount > 0, "Partition count should be strictly positive");

        int shardConcurrency = Math.max(1, configurations.eventBusConfiguration().maxConcurrency() / partitionCount);
        return new RabbitMQEventBus.Configurations(configurations.rabbitMQConfiguration(), configurations.retryBackoff(),
            new EventBus.Configuration(shardConcurrency, configurations.eventBusConfiguration().executionTimeout()));
    }

    private final ImmutableList<TmailGroupRegistrationHandler> delegates;

    public AggregatedTmailGroupRegistrationHandler(List<NamingStrategy> namingStrategies, EventSerializer eventSerializer,
                                                   ReactorRabbitMQChannelPool channelPool, Sender sender,
                                                   ReceiverProvider receiverProvider,
                                                   EventDeadLetters eventDeadLetters,
                                                   ListenerExecutor listenerExecutor,
                                                   RabbitMQEventBus.Configurations configurations) {
        this(buildDelegates(namingStrategies, eventSerializer, channelPool, sender, receiverProvider,
            eventDeadLetters, listenerExecutor, configurations));
    }

    @VisibleForTesting
    AggregatedTmailGroupRegistrationHandler(List<TmailGroupRegistrationHandler> delegates) {
        Preconditions.checkArgument(!delegates.isEmpty(), "Tmail group registration handler requires at least one delegate");
        this.delegates = ImmutableList.copyOf(delegates);
    }

    @Override
    public Stream<GroupRegistration> synchronousGroupRegistrations() {
        return firstDelegate().synchronousGroupRegistrations();
    }

    @Override
    public Mono<Void> reDeliver(Group group, Event event) {
        return randomDelegate().reDeliver(group, event);
    }

    @Override
    public void stop() {
        delegates.forEach(TmailGroupRegistrationHandler::stop);
    }

    @Override
    public void restart() {
        delegates.forEach(TmailGroupRegistrationHandler::restart);
    }

    @Override
    public Registration register(EventListener.ReactiveEventListener listener, Group group) {
        ImmutableList.Builder<Registration> successfulRegistrations = ImmutableList.builder();
        try {
            delegates.forEach(delegate -> successfulRegistrations.add(delegate.register(listener, group)));
        } catch (RuntimeException e) {
            rollbackRegisteredRegistrations(successfulRegistrations.build());
            throw e;
        }

        return () -> Flux.fromIterable(successfulRegistrations.build())
            .concatMap(Registration::unregister)
            .then();
    }

    @Override
    public Collection<Group> registeredGroups() {
        return firstDelegate().registeredGroups();
    }

    private TmailGroupRegistrationHandler firstDelegate() {
        return delegates.getFirst();
    }

    private TmailGroupRegistrationHandler randomDelegate() {
        return delegates.get(ThreadLocalRandom.current().nextInt(delegates.size()));
    }

    private void rollbackRegisteredRegistrations(List<Registration> successfulRegistrations) {
        Flux.fromIterable(successfulRegistrations)
            .concatMap(registration -> Mono.from(registration.unregister()))
            .then()
            .block();
    }

}
