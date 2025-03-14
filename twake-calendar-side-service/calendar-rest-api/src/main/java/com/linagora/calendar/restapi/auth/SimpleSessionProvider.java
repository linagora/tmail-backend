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

package com.linagora.calendar.restapi.auth;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.store.RandomMailboxSessionIdGenerator;

import com.google.common.collect.ImmutableList;

public class SimpleSessionProvider {
    private final RandomMailboxSessionIdGenerator idGenerator;

    @Inject
    public SimpleSessionProvider(RandomMailboxSessionIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    public MailboxSession createSession(Username username) {
        return new MailboxSession(MailboxSession.SessionId.of(idGenerator.nextId()), username, Optional.of(username), ImmutableList.of(), '.', MailboxSession.SessionType.User);
    }
}
