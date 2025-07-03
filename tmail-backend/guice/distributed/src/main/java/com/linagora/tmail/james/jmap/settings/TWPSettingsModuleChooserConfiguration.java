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

package com.linagora.tmail.james.jmap.settings;

import java.io.FileNotFoundException;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration$;

public record TWPSettingsModuleChooserConfiguration(boolean enabled) {
    private static final Logger LOGGER = LoggerFactory.getLogger(TWPSettingsModuleChooserConfiguration.class);
    private static final boolean TWP_SETTINGS_DISABLED = false;

    public static TWPSettingsModuleChooserConfiguration parse(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration jmapConfiguration = propertiesProvider.getConfiguration("jmap");

            boolean enabled = Optional.ofNullable(jmapConfiguration.getString(JMAPExtensionConfiguration$.MODULE$.SETTINGS_READONLY_PROPERTIES_PROVIDERS(), null))
                .map(value -> value.contains(TWPReadOnlyPropertyProvider.class.getSimpleName()))
                .orElse(TWP_SETTINGS_DISABLED);

            LOGGER.info("TWP settings module is {}.", enabled ? "enabled" : "disabled");
            return new TWPSettingsModuleChooserConfiguration(enabled);
        } catch (FileNotFoundException e) {
            LOGGER.info("TWP settings module is disabled");
            return new TWPSettingsModuleChooserConfiguration(TWP_SETTINGS_DISABLED);
        }
    }
}
