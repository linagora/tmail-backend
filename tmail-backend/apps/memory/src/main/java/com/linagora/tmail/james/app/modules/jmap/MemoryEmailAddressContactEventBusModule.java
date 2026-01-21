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

package com.linagora.tmail.james.app.modules.jmap;

import static com.linagora.tmail.mailets.IndexContacts.TMAIL_EVENT_BUS_INJECT_NAME;

import java.util.Set;

import org.apache.james.events.EventBus;
import org.apache.james.events.EventListener;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactListener;

public class MemoryEmailAddressContactEventBusModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class, Names.named(TMAIL_EVENT_BUS_INJECT_NAME))
            .addBinding()
            .to(EmailAddressContactListener.class);
    }

    @ProvidesIntoSet
    public InitializationOperation registerListener(
            @Named(TMAIL_EVENT_BUS_INJECT_NAME) EventBus eventBus,
            @Named(TMAIL_EVENT_BUS_INJECT_NAME) Set<EventListener.ReactiveGroupEventListener> tmailListeners) {
        return InitilizationOperationBuilder
                .forClass(EmailAddressContactEventLoader.class)
                .init(() -> tmailListeners.forEach(eventBus::register));
    }

    @Provides
    @Singleton
    @Named(TMAIL_EVENT_BUS_INJECT_NAME)
    public EventBus provideInVMEventBus(EventBus eventBus) {
        return eventBus;
    }

    public static class EmailAddressContactEventLoader implements Startable {

    }
}


