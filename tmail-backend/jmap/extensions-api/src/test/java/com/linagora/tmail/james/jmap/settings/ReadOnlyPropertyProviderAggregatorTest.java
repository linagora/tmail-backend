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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

class ReadOnlyPropertyProviderAggregatorTest {
    private static final JmapSettingsKey LANGUAGE_KEY = JmapSettingsKey.liftOrThrow("language");
    private static final JmapSettingsValue ENGLISH_VALUE = new JmapSettingsValue("en");
    private static final JmapSettingsKey TIMEZONE_KEY = JmapSettingsKey.liftOrThrow("timezone");
    private static final JmapSettingsValue UTC_VALUE = new JmapSettingsValue("UTC");
    private static final JmapSettingsKey DUPLICATE_KEY = JmapSettingsKey.liftOrThrow("duplicate_key");
    private static final JmapSettingsValue VALUE_1 = new JmapSettingsValue("value1");
    private static final JmapSettingsValue VALUE_2 = new JmapSettingsValue("value1");

    @Test
    void shouldReturnEmptyWhenNoProviders() {
        ReadOnlyPropertyProviderAggregator testee = new ReadOnlyPropertyProviderAggregator(Set.of());

        assertThat(testee.readOnlySettings()).isEmpty();
        assertThat(testee.resolveSettings(Username.of("user"))).isEmpty();
    }

    @Test
    void readOnlySettingsShouldAggregateKeysFromProviders() {
        ReadOnlyPropertyProvider fixedLanguageReadOnlyPropertyProvider = new FixedLanguageReadOnlyPropertyProvider();
        ReadOnlyPropertyProvider timeZoneProvider = new TestingReadOnlyPropertyProvider(Map.of(TIMEZONE_KEY, UTC_VALUE));

        ReadOnlyPropertyProviderAggregator testee = new ReadOnlyPropertyProviderAggregator(Set.of(fixedLanguageReadOnlyPropertyProvider, timeZoneProvider));

        assertThat(testee.readOnlySettings()).containsExactlyInAnyOrder(LANGUAGE_KEY, TIMEZONE_KEY);
        assertThat(testee.resolveSettings(Username.of("user")))
            .containsExactlyInAnyOrderEntriesOf(ImmutableMap.of(
                LANGUAGE_KEY, ENGLISH_VALUE,
                TIMEZONE_KEY, UTC_VALUE));
    }

    @Test
    void shouldKeepSettingOfPrecedingProviderWhenDuplicateKeys() {
        ReadOnlyPropertyProvider provider1 = new TestingReadOnlyPropertyProvider(Map.of(DUPLICATE_KEY, VALUE_1));
        ReadOnlyPropertyProvider provider2 = new TestingReadOnlyPropertyProvider(Map.of(DUPLICATE_KEY, VALUE_2));

        ReadOnlyPropertyProviderAggregator testee = new ReadOnlyPropertyProviderAggregator(Set.of(provider1, provider2));

        assertThat(testee.readOnlySettings()).containsOnly(DUPLICATE_KEY);
        assertThat(testee.resolveSettings(Username.of("user")))
            .containsExactlyInAnyOrderEntriesOf(ImmutableMap.of(DUPLICATE_KEY, VALUE_1));
    }
}
