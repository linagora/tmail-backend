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

import java.io.FileNotFoundException;
import java.time.Clock;
import java.util.Optional;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.rabbitmq.MonitoredDeadLetterQueue;
import org.apache.james.backends.rabbitmq.MonitoredRabbitMQConsumers;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.events.EventListener;
import org.apache.james.mailbox.ReadOnlyAnnotationPredicate;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.apache.james.webadmin.Routes;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.rabbitmq.ManagedRabbitMQConsumer;
import com.linagora.tmail.webadmin.mailinglist.MailingListRepository;
import com.linagora.tmail.webadmin.mailinglist.MailingListsNotConfiguredException;

import reactor.core.publisher.Mono;

/**
 * Gives each TwakeSpace space a team mailbox. Load it with
 * {@code guice.extension.module=com.linagora.tmail.space.SpaceModule} in {@code extensions.properties}; it reads
 * {@code space.properties}.
 */
public class SpaceModule extends AbstractModule {
    private static final String SPACE_CONSUMER = "space-consumer";

    @Override
    protected void configure() {
        bind(SpaceMailboxes.class).in(Scopes.SINGLETON);
        bind(SpaceReconciler.class).in(Scopes.SINGLETON);
        bind(SpacePurge.class).in(Scopes.SINGLETON);

        OptionalBinder.newOptionalBinder(binder(), MailingListRepository.class);

        Multibinder.newSetBinder(binder(), ReadOnlyAnnotationPredicate.class)
            .addBinding()
            .toInstance(SpaceAnnotations.READ_ONLY);

        Multibinder<EventListener.ReactiveGroupEventListener> listeners = Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class);
        listeners.addBinding().to(SpaceActivityListener.class);
        listeners.addBinding().to(TeamMailboxEventsListener.class);

        Multibinder.newSetBinder(binder(), Routes.class)
            .addBinding()
            .to(SpaceRoutes.class);
    }

    @Provides
    @Singleton
    SpaceConfiguration provideConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        return readConfiguration(propertiesProvider)
            .map(SpaceConfiguration::from)
            .orElse(SpaceConfiguration.DEFAULT);
    }

    @Provides
    @Singleton
    TeamMailboxEventsConfiguration provideTeamMailboxEventsConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        return readConfiguration(propertiesProvider)
            .map(TeamMailboxEventsConfiguration::from)
            .orElse(TeamMailboxEventsConfiguration.DEFAULT);
    }

    private Optional<Configuration> readConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return Optional.of(propertiesProvider.getConfiguration("space"));
        } catch (FileNotFoundException e) {
            return Optional.empty();
        }
    }

    @Provides
    @Singleton
    SpacePublisher providePublisher(SpaceConfiguration configuration, RabbitMQConfiguration rabbitMQConfiguration, Clock clock) {
        return SpacePublisher.create(configuration, rabbitMQConfiguration, clock);
    }

    @Provides
    @Singleton
    SpaceReconciler.MailingLists provideMailingLists(Optional<MailingListRepository> mailingListRepository) {
        return mailingListRepository
            .<SpaceReconciler.MailingLists>map(repository -> address -> {
                try {
                    return repository.get(address).isPresent();
                } catch (MailingListsNotConfiguredException e) {
                    return false;
                }
            })
            .orElse(SpaceReconciler.MailingLists.NONE);
    }

    @Provides
    @Singleton
    @Named(SPACE_CONSUMER)
    ManagedRabbitMQConsumer provideConsumer(SpacePublisher publisher, RabbitMQConfiguration rabbitMQConfiguration,
                                            SpaceConfiguration configuration, SpaceReconciler reconciler) {
        return SpaceEventsConsumer.create(publisher, rabbitMQConfiguration, configuration,
            new SpaceEventsConsumer(reconciler, publisher, configuration));
    }

    @ProvidesIntoSet
    MonitoredRabbitMQConsumers spaceConsumers(SpacePublisher publisher, SpaceConfiguration configuration,
                                              @Named(SPACE_CONSUMER) ManagedRabbitMQConsumer consumer) {
        return MonitoredRabbitMQConsumers.of("TwakeSpace events", publisher.connectionPool(),
            () -> ImmutableList.of(configuration.queue()),
            connection -> Mono.fromRunnable(consumer::restart));
    }

    @ProvidesIntoSet
    MonitoredDeadLetterQueue spaceDeadLetterQueue(RabbitMQConfiguration rabbitMQConfiguration, SpaceConfiguration configuration) {
        return new MonitoredDeadLetterQueue(rabbitMQConfiguration, configuration.deadLetterQueue());
    }

    @ProvidesIntoSet
    InitializationOperation initializeConsumer(@Named(SPACE_CONSUMER) ManagedRabbitMQConsumer consumer) {
        return InitilizationOperationBuilder
            .forClass(ManagedRabbitMQConsumer.class)
            .init(consumer::init);
    }

    @ProvidesIntoSet
    InitializationOperation initializePurge(SpacePurge purge) {
        return InitilizationOperationBuilder
            .forClass(SpacePurge.class)
            .init(purge::start);
    }
}
