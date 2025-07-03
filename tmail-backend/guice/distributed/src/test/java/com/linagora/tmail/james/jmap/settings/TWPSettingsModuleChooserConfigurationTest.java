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

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.FakePropertiesProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TWPSettingsModuleChooserConfigurationTest {
    @Test
    void twpSettingsShouldBeDisabledByDefault() throws ConfigurationException {
        PropertiesConfiguration jmapConfiguration = new PropertiesConfiguration();
        FakePropertiesProvider propertiesProvider = FakePropertiesProvider.builder()
            .register("jmap", jmapConfiguration)
            .build();

        TWPSettingsModuleChooserConfiguration configuration = TWPSettingsModuleChooserConfiguration.parse(propertiesProvider);

        assertThat(configuration.enabled()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "TWPReadOnlyPropertyProvider",
        "com.linagora.tmail.james.jmap.settings.TWPReadOnlyPropertyProvider",
        "com.linagora.tmail.james.jmap.settings.TWPReadOnlyPropertyProvider, com.linagora.tmail.james.jmap.settings.FixedLanguageReadOnlyPropertyProvider"})
    void twpSettingsShouldBeEnabledWhenTWPReadOnlyPropertyProviderConfigured(String readOnlyPropertyProviders) throws ConfigurationException {
        PropertiesConfiguration jmapConfiguration = new PropertiesConfiguration();
        jmapConfiguration.addProperty("settings.readonly.properties.providers", readOnlyPropertyProviders);
        FakePropertiesProvider propertiesProvider = FakePropertiesProvider.builder()
            .register("jmap", jmapConfiguration)
            .build();

        TWPSettingsModuleChooserConfiguration configuration = TWPSettingsModuleChooserConfiguration.parse(propertiesProvider);

        assertThat(configuration.enabled()).isTrue();
    }
}
