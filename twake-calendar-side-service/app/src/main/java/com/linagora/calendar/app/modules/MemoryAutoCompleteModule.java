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

package com.linagora.calendar.app.modules;

import org.apache.james.core.MailAddress;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.utils.GuiceProbe;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngine;

import reactor.core.publisher.Mono;

public class MemoryAutoCompleteModule extends AbstractModule {
    public static class Probe implements GuiceProbe {
        private final EmailAddressContactSearchEngine engine;

        @Inject
        public Probe(EmailAddressContactSearchEngine engine) {
            this.engine = engine;
        }

        public void add(String user, String email, String firstName, String lastName) {
            try {
                Mono.from(engine.index(AccountId.fromString(user), new ContactFields(new MailAddress(email), firstName, lastName)))
                    .block();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected void configure() {
        bind(InMemoryEmailAddressContactSearchEngine.class).in(Scopes.SINGLETON);
        bind(EmailAddressContactSearchEngine.class).to(InMemoryEmailAddressContactSearchEngine.class);

        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(Probe.class);
    }
}
