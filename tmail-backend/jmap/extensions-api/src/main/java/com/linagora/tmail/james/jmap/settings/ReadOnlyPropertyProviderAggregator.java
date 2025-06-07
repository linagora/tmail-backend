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
 *******************************************************************/

package com.linagora.tmail.james.jmap.settings;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.core.Username;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReadOnlyPropertyProviderAggregator implements ReadOnlyPropertyProvider {
    private final Set<ReadOnlyPropertyProvider> providers;

    @Inject
    public ReadOnlyPropertyProviderAggregator(Set<ReadOnlyPropertyProvider> providers) {
        this.providers = providers;
    }

    @Override
    public List<JmapSettingsKey> readOnlySettings() {
        return providers.stream()
            .flatMap(provider -> provider.readOnlySettings().stream())
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public Mono<Map<JmapSettingsKey, JmapSettingsValue>> resolveSettings(Username username) {
        return Flux.fromIterable(providers)
            .flatMap(provider -> Mono.from(provider.resolveSettings(username))
                .flatMapIterable(Map::entrySet))
            .collect(ImmutableMap.toImmutableMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (existingValue, newValue) -> existingValue));
    }
}
