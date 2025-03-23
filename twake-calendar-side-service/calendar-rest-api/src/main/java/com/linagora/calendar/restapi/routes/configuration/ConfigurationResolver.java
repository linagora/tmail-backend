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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.MailboxSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.linagora.calendar.restapi.api.ConfigurationDocument;
import com.linagora.calendar.restapi.api.ConfigurationEntryResolver;
import com.linagora.calendar.restapi.api.ConfigurationKey;
import com.linagora.calendar.restapi.api.ModuleName;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ConfigurationResolver {
    record Entry(ModuleName moduleName, ConfigurationKey configurationKey, JsonNode node) {

    }

    private final Table<ModuleName, ConfigurationKey, ConfigurationEntryResolver> resolvers;
    private final FallbackConfigurationEntryResolver fallbackConfigurationEntryResolver;

    @Inject
    public ConfigurationResolver(Set<ConfigurationEntryResolver> resolvers, FallbackConfigurationEntryResolver fallbackConfigurationEntryResolver) {
        this.resolvers = resolvers.stream()
            .collect(ImmutableTable.toImmutableTable(ConfigurationEntryResolver::moduleName, ConfigurationEntryResolver::configurationKey, r -> r));
        this.fallbackConfigurationEntryResolver = fallbackConfigurationEntryResolver;
    }

    public Mono<ConfigurationDocument> resolve(List<Pair<ModuleName, ConfigurationKey>> request, MailboxSession session) {
        return Flux.fromIterable(request)
            .flatMap(entry -> Optional.ofNullable(resolvers.get(entry.getKey(), entry.getValue()))
                .map(r -> r.resolve(session))
                .orElseGet(() -> fallbackConfigurationEntryResolver.resolve(entry.getKey(), entry.getValue(), session))
                .map(node -> new Entry(entry.getKey(), entry.getValue(), node)))
            .collect(ImmutableTable.toImmutableTable(Entry::moduleName, Entry::configurationKey, Entry::node))
            .map(ConfigurationDocument::new);
    }

    public Mono<ConfigurationDocument> resolveAll(MailboxSession session) {
        // Returns only explicitly implemented configuration resolvers
        return Flux.fromIterable(resolvers.cellSet())
            .flatMap(cell -> cell.getValue().resolve(session).map(node -> new Entry(cell.getRowKey(), cell.getColumnKey(), node)))
            .collect(ImmutableTable.toImmutableTable(Entry::moduleName, Entry::configurationKey, Entry::node))
            .map(ConfigurationDocument::new);
    }
}
