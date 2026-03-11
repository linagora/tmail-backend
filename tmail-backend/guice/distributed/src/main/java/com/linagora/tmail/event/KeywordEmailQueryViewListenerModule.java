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

import org.apache.james.events.EventListener;
import org.apache.james.mailbox.cassandra.DeleteMessageListener;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.linagora.tmail.james.jmap.event.KeywordEmailQueryViewListener;

public class KeywordEmailQueryViewListenerModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class)
            .addBinding()
            .to(KeywordEmailQueryViewListener.class);

        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class, Names.named(DeleteMessageListener.CONTENT_DELETION))
            .addBinding()
            .to(KeywordEmailQueryViewListener.class);
    }
}
