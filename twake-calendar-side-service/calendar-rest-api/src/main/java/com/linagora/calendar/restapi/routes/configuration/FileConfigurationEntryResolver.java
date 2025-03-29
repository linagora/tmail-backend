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
import java.util.function.Function;

import jakarta.inject.Inject;

import org.apache.james.mailbox.MailboxSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.linagora.calendar.restapi.RestApiConfiguration;
import com.linagora.calendar.restapi.api.ConfigurationEntryResolver;
import com.linagora.calendar.restapi.api.ConfigurationKey;
import com.linagora.calendar.restapi.api.ModuleName;

import reactor.core.publisher.Flux;

public class FileConfigurationEntryResolver implements ConfigurationEntryResolver {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static Function<RestApiConfiguration, ObjectNode> davServerConfiguration() {
        return configuration -> {
            ObjectNode frontendNode = OBJECT_MAPPER.createObjectNode();

            frontendNode.put("url", configuration.getDavURL().toString());

            ObjectNode backendNode = OBJECT_MAPPER.createObjectNode();
            backendNode.put("url", configuration.getDavURL().toString());

            ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
            objectNode.put("frontend", frontendNode);
            objectNode.put("backend", backendNode);
            return objectNode;
        };
    }

    private static Function<RestApiConfiguration, ObjectNode> calendarSharingEnabled() {
        return configuration -> {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("isSharingCalendarEnabled", configuration.isCalendarSharingEnabled());
            return node;
        };
    }

    private static final Table<ModuleName, ConfigurationKey, Function<RestApiConfiguration, ObjectNode>> TABLE = ImmutableTable.<ModuleName, ConfigurationKey, Function<RestApiConfiguration, ObjectNode>>builder()
        .put(new ModuleName("core"), new ConfigurationKey("davserver"), davServerConfiguration())
        .put(new ModuleName("linagora.esn.calendar"), new ConfigurationKey("features"), calendarSharingEnabled())
        .build();

    public static final ImmutableSet<EntryIdentifier> KEYS = TABLE.cellSet()
        .stream()
        .map(cell -> new EntryIdentifier(cell.getRowKey(), cell.getColumnKey()))
        .collect(ImmutableSet.toImmutableSet());

    private final RestApiConfiguration configuration;

    @Inject
    public FileConfigurationEntryResolver(RestApiConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Flux<Entry> resolve(Set<EntryIdentifier> ids, MailboxSession session) {
        return Flux.fromIterable(Sets.intersection(ids, KEYS))
            .map(id -> new Entry(id.moduleName(), id.configurationKey(), TABLE.get(id.moduleName(), id.configurationKey()).apply(configuration)));
    }

    @Override
    public Set<EntryIdentifier> entryIdentifiers() {
        return KEYS;
    }
}
