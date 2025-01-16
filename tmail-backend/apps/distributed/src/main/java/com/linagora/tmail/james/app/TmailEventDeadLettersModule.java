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

import static com.linagora.tmail.event.TmailEventModule.TMAIL_EVENT_BUS_INJECT_NAME;

import jakarta.inject.Named;

import org.apache.james.events.CassandraEventDeadLetters;
import org.apache.james.events.CassandraEventDeadLettersDAO;
import org.apache.james.events.CassandraEventDeadLettersGroupDAO;
import org.apache.james.events.EventDeadLetters;

import com.datastax.oss.driver.api.core.CqlSession;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.linagora.tmail.event.TmailEventSerializer;

public class TmailEventDeadLettersModule extends AbstractModule {

    @Provides
    @Singleton
    @Named(TMAIL_EVENT_BUS_INJECT_NAME)
    EventDeadLetters provideEventDeadLetters(CassandraEventDeadLettersGroupDAO cassandraEventDeadLettersGroupDAO,
                                             CqlSession session,
                                             TmailEventSerializer tmailEventSerializer) {
        return new CassandraEventDeadLetters(new CassandraEventDeadLettersDAO(session, tmailEventSerializer),
            cassandraEventDeadLettersGroupDAO);
    }
}
