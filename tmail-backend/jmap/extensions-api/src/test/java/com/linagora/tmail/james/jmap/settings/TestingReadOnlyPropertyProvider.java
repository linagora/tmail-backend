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
import java.util.stream.Collectors;

import org.apache.james.core.Username;

public class TestingReadOnlyPropertyProvider implements ReadOnlyPropertyProvider {
    public static TestingReadOnlyPropertyProvider of(Map<String, String> settings) {
        return new TestingReadOnlyPropertyProvider(settings
            .entrySet()
            .stream()
            .map(entry -> Map.entry(JmapSettingsKey.liftOrThrow(entry.getKey()),
                new JmapSettingsValue(entry.getValue())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private final Map<JmapSettingsKey, JmapSettingsValue> settings;

    public TestingReadOnlyPropertyProvider(Map<JmapSettingsKey, JmapSettingsValue> settings) {
        this.settings = settings;
    }

    @Override
    public List<JmapSettingsKey> readOnlySettings() {
        return settings.keySet().stream().toList();
    }

    @Override
    public Map<JmapSettingsKey, JmapSettingsValue> resolveSettings(Username username) {
        return settings;
    }
}
