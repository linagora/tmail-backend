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

import jakarta.inject.Named;

import org.apache.james.events.EventBus;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactListener;

public class EmailAddressContactRabbitMQEventBusModule extends AbstractModule {
    @ProvidesIntoSet
    public InitializationOperation registerListener(
        @Named(TmailEventModule.TMAIL_EVENT_BUS_INJECT_NAME) EventBus eventBus,
        EmailAddressContactListener emailAddressContactListener) {
        return InitilizationOperationBuilder
            .forClass(EmailAddressContactEventLoader.class)
            .init(() -> eventBus.register(emailAddressContactListener));
    }

    public static class EmailAddressContactEventLoader implements Startable {

    }
}
