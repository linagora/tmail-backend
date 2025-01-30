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

package com.linagora.tmail;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.configuration.DavConfiguration;
import com.linagora.tmail.configuration.OpenPaasConfiguration;

public record OpenPaasModuleChooserConfiguration(boolean enabled,
                                                 boolean shouldEnableDavServerInteraction,
                                                 boolean contactsConsumerEnabled) {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenPaasModuleChooserConfiguration.class);
    public static final boolean ENABLED = true;
    public static final boolean DISABLED = false;
    public static final boolean ENABLE_CONTACTS_CONSUMER = true;
    public static final boolean ENABLE_DAV = true;

    public static OpenPaasModuleChooserConfiguration parse(PropertiesProvider propertiesProvider) throws
        ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration("openpaas");
            boolean contactsConsumerEnabled = OpenPaasConfiguration.isConfiguredContactConsumer(configuration);
            boolean isDavConfigured = DavConfiguration.isConfigured(configuration);
            LOGGER.info("OpenPaas module is turned on. Contacts consumer is enabled: {}, Dav is enabled: {}",
                contactsConsumerEnabled, isDavConfigured);
            return new OpenPaasModuleChooserConfiguration(ENABLED, isDavConfigured, contactsConsumerEnabled);
        } catch (FileNotFoundException e) {
            LOGGER.info("OpenPaas module is turned off.");
            return new OpenPaasModuleChooserConfiguration(DISABLED, !ENABLE_DAV, !ENABLE_CONTACTS_CONSUMER);
        }
    }
}
