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

package com.linagora.calendar.restapi.api;

import java.util.Set;

import org.apache.james.mailbox.MailboxSession;

import com.fasterxml.jackson.databind.JsonNode;

import reactor.core.publisher.Flux;

public interface ConfigurationEntryResolver {
    record EntryIdentifier(ModuleName moduleName, ConfigurationKey configurationKey) {

    }

    record Entry(ModuleName moduleName, ConfigurationKey configurationKey, JsonNode node) {

    }

    Flux<Entry> resolve(Set<EntryIdentifier> ids, MailboxSession session);

    default Flux<Entry> resolveAll(MailboxSession session) {
        return resolve(entryIdentifiers(), session);
    }

    Set<EntryIdentifier> entryIdentifiers();


}
