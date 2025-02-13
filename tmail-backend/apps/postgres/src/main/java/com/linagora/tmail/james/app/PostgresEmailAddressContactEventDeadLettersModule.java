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

package com.linagora.tmail.james.app;

import jakarta.inject.Named;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.PostgresEventDeadLetters;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.linagora.tmail.james.jmap.EmailAddressContactInjectKeys;
import com.linagora.tmail.james.jmap.contact.TmailJmapEventSerializer;

public class PostgresEmailAddressContactEventDeadLettersModule extends AbstractModule {

    @Provides
    @Singleton
    @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE)
    EventDeadLetters provideEventDeadLettersForAutoComplete(PostgresExecutor postgresExecutor, TmailJmapEventSerializer tmailJmapEventSerializer) {
        return new PostgresEventDeadLetters(postgresExecutor, tmailJmapEventSerializer);
    }
}
