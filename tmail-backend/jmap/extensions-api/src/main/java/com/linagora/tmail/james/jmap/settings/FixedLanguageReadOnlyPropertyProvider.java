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

import org.apache.james.core.Username;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;

public class FixedLanguageReadOnlyPropertyProvider implements ReadOnlyPropertyProvider {
    private static final JmapSettingsKey LANGUAGE = JmapSettingsKey.liftOrThrow("language");
    private static final JmapSettingsValue ENGLISH = new JmapSettingsValue("en");
    private static final List<JmapSettingsKey> SETTINGS_KEYS = ImmutableList.of(LANGUAGE);
    private static final Map<JmapSettingsKey, JmapSettingsValue> SETTINGS = ImmutableMap.of(LANGUAGE, ENGLISH);

    @Override
    public List<JmapSettingsKey> readOnlySettings() {
        return SETTINGS_KEYS;
    }

    @Override
    public Mono<Map<JmapSettingsKey, JmapSettingsValue>> resolveSettings(Username username) {
        return Mono.just(SETTINGS);
    }
}
