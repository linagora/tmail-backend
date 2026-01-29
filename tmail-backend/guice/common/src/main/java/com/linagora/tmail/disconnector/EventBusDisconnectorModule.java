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

package com.linagora.tmail.disconnector;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.james.DisconnectorNotifier;
import org.apache.james.events.EventBus;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class EventBusDisconnectorModule extends AbstractModule {
    @Provides
    @Singleton
    DisconnectorNotifier disconnectorNotifier(@Named("TMAIL_EVENT_BUS") EventBus tmailEventBus) {
        return new EventBusDisconnectorNotifier(tmailEventBus);
    }
}
