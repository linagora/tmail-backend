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

package com.linagora.tmail.space;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The space extension is loaded when {@code space.properties} says {@code enabled=true}. */
public record SpaceModuleChooserConfiguration(boolean enabled) {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpaceModuleChooserConfiguration.class);
    private static final boolean SPACE_DISABLED = false;

    public static SpaceModuleChooserConfiguration parse(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            boolean enabled = propertiesProvider.getConfiguration("space").getBoolean("enabled", SPACE_DISABLED);
            LOGGER.info("Twake Space module is {}.", enabled ? "enabled" : "disabled");
            return new SpaceModuleChooserConfiguration(enabled);
        } catch (FileNotFoundException e) {
            LOGGER.info("Twake Space module is disabled");
            return new SpaceModuleChooserConfiguration(SPACE_DISABLED);
        }
    }
}
