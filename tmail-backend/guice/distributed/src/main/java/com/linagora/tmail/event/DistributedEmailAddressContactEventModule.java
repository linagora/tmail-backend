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

import jakarta.inject.Named;

import org.apache.james.events.EventBus;
import org.apache.james.events.EventBusId;
import org.apache.james.events.EventBusName;
import org.apache.james.events.EventSerializer;
import org.apache.james.events.NamingStrategy;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Names;
import com.linagora.tmail.james.jmap.EmailAddressContactInjectKeys;
import com.linagora.tmail.james.jmap.contact.TmailJmapEventSerializer;

public class DistributedEmailAddressContactEventModule extends AbstractModule {
    public static final NamingStrategy EMAIL_ADDRESS_CONTACT_NAMING_STRATEGY = new NamingStrategy(new EventBusName("emailAddressContactEvent"));

    @Override
    protected void configure() {
        bind(EventBusId.class).annotatedWith(Names.named(EmailAddressContactInjectKeys.AUTOCOMPLETE)).toInstance(EventBusId.random());

        Multibinder.newSetBinder(binder(), EventSerializer.class)
            .addBinding()
            .to(TmailJmapEventSerializer.class);
    }

    @ProvidesIntoSet
    EventBus registerEventBus(@Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) EventBus eventBus) {
        return eventBus;
    }
}
