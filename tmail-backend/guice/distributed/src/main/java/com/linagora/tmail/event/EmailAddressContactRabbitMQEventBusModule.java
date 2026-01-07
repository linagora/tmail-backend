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
 *******************************************************************/

package com.linagora.tmail.event;

import static com.linagora.tmail.event.DistributedEmailAddressContactEventModule.EMAIL_ADDRESS_CONTACT_NAMING_STRATEGY;

import jakarta.inject.Named;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventBusId;
import org.apache.james.events.RabbitMQEventBus;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.RoutingKeyConverter;
import org.apache.james.jmap.change.Factory;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.james.jmap.EmailAddressContactInjectKeys;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactListener;
import com.linagora.tmail.james.jmap.contact.TmailJmapEventSerializer;

public class EmailAddressContactRabbitMQEventBusModule extends AbstractModule {
    @ProvidesIntoSet
    InitializationOperation workQueue(@Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) RabbitMQEventBus instance,
                                      EmailAddressContactListener emailAddressContactListener) {
        return InitilizationOperationBuilder
            .forClass(RabbitMQEventBus.class)
            .init(() -> {
                instance.start();
                instance.register(emailAddressContactListener);
            });
    }

    @Provides
    @Singleton
    @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE)
    RabbitMQEventBus provideEmailAddressContactEventBus(RabbitMQEventBus.Factory eventBusFactory,
                                                        TmailJmapEventSerializer eventSerializer,
                                                        RetryBackoffConfiguration retryBackoffConfiguration,
                                                        @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) EventBusId eventBusId,
                                                        RabbitMQConfiguration configuration,
                                                        EventBus.Configuration eventBusConfiguration) {
        return eventBusFactory.create(eventBusId, EMAIL_ADDRESS_CONTACT_NAMING_STRATEGY, new RoutingKeyConverter(ImmutableSet.of(new Factory())), eventSerializer, new RabbitMQEventBus.Configurations(configuration, retryBackoffConfiguration, eventBusConfiguration));
    }

    @Provides
    @Singleton
    @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE)
    EventBus provideEmailAddressContactEventBus(@Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) RabbitMQEventBus eventBus) {
        return eventBus;
    }
}
