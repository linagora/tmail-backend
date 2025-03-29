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

package com.linagora.calendar.restapi.routes.configuration;

import java.util.Set;

import org.apache.james.mailbox.MailboxSession;

import com.fasterxml.jackson.databind.node.NullNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.linagora.calendar.restapi.api.ConfigurationEntryResolver;
import com.linagora.calendar.restapi.api.ConfigurationKey;
import com.linagora.calendar.restapi.api.ModuleName;

import reactor.core.publisher.Flux;

public class NullConstantConfigurationEntryResolver implements ConfigurationEntryResolver {
    @Override
    public Flux<Entry> resolve(Set<EntryIdentifier> ids, MailboxSession session) {
        return Flux.fromIterable(Sets.intersection(ids, entryIdentifiers()))
            .map(id -> new Entry(id.moduleName(), id.configurationKey(), NullNode.getInstance()));
    }

    @Override
    public Set<EntryIdentifier> entryIdentifiers() {
        return ImmutableSet.of(new ConfigurationEntryResolver.EntryIdentifier(new ModuleName("core"), new ConfigurationKey("allowDomainAdminToManageUserEmails")),
            new ConfigurationEntryResolver.EntryIdentifier(new ModuleName("core"), new ConfigurationKey("homePage")));
    }
}
