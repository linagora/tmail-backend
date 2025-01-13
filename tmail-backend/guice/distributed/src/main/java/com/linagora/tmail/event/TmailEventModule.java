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

package com.linagora.tmail.event;

import java.util.Set;

import jakarta.inject.Named;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventBusId;
import org.apache.james.events.EventBusName;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.EventSerializer;
import org.apache.james.events.NamingStrategy;
import org.apache.james.events.RabbitMQEventBus;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.RoutingKeyConverter;
import org.apache.james.jmap.change.Factory;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Names;
import com.linagora.tmail.common.event.TmailInjectNameConstants;
import com.linagora.tmail.common.event.TmailReactiveGroupEventListener;

import reactor.rabbitmq.Sender;

public class TmailEventModule extends AbstractModule {
    public static final NamingStrategy TMAIL_NAMING_STRATEGY = new NamingStrategy(new EventBusName("tmailEvent"));

    @Override
    protected void configure() {
        bind(TmailEventSerializer.class).in(Scopes.SINGLETON);
        bind(EventBusId.class).annotatedWith(Names.named(TmailInjectNameConstants.TMAIL_EVENT_BUS_INJECT_NAME)).toInstance(EventBusId.random());

        Multibinder.newSetBinder(binder(), EventSerializer.class)
            .addBinding()
            .to(TmailEventSerializer.class);
        Multibinder.newSetBinder(binder(), TmailReactiveGroupEventListener.class);
    }

    @ProvidesIntoSet
    InitializationOperation workQueue(@Named(TmailInjectNameConstants.TMAIL_EVENT_BUS_INJECT_NAME) RabbitMQEventBus instance,
                                      Set<TmailReactiveGroupEventListener> tmailReactiveGroupEventListeners) {
        return InitilizationOperationBuilder
            .forClass(RabbitMQEventBus.class)
            .init(() -> {
                instance.start();
                tmailReactiveGroupEventListeners.forEach(instance::register);
            });
    }

    @Provides
    @Singleton
    @Named(TmailInjectNameConstants.TMAIL_EVENT_BUS_INJECT_NAME)
    RabbitMQEventBus provideEmailAddressContactEventBus(Sender sender, ReceiverProvider receiverProvider,
                                                        RetryBackoffConfiguration retryBackoffConfiguration,
                                                        @Named(TmailInjectNameConstants.TMAIL_EVENT_BUS_INJECT_NAME) EventDeadLetters eventDeadLetters,
                                                        MetricFactory metricFactory, ReactorRabbitMQChannelPool channelPool,
                                                        @Named(TmailInjectNameConstants.TMAIL_EVENT_BUS_INJECT_NAME) EventBusId eventBusId,
                                                        RabbitMQConfiguration configuration,
                                                        TmailEventSerializer tmailEventSerializer) {
        return new RabbitMQEventBus(
            TMAIL_NAMING_STRATEGY,
            sender, receiverProvider, tmailEventSerializer, retryBackoffConfiguration, new RoutingKeyConverter(ImmutableSet.of(new Factory())),
            eventDeadLetters, metricFactory, channelPool, eventBusId, configuration);
    }

    @Provides
    @Singleton
    @Named(TmailInjectNameConstants.TMAIL_EVENT_BUS_INJECT_NAME)
    EventBus provideEmailAddressContactEventBus(@Named(TmailInjectNameConstants.TMAIL_EVENT_BUS_INJECT_NAME) RabbitMQEventBus eventBus) {
        return eventBus;
    }

    @ProvidesIntoSet
    EventBus registerEventBus(@Named(TmailInjectNameConstants.TMAIL_EVENT_BUS_INJECT_NAME) EventBus eventBus) {
        return eventBus;
    }
}
