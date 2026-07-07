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

package com.linagora.tmail.webadmin.mailinglist;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.linagora.tmail.mailet.MailingListConfiguration;

/**
 * Provides the centralized {@link MailingListConfiguration} read from {@code mailingLists.properties}. When the file
 * is absent, an empty configuration is provided so mailing list components fall back to their per-component settings.
 *
 * <p>This module carries no LDAP dependency and is meant to be loaded unconditionally, so that the mailing list
 * mailets and SMTP handler can always resolve their {@link MailingListConfiguration} injection.</p>
 */
public class MailingListConfigurationModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailingListConfigurationModule.class);

    @Provides
    @Singleton
    MailingListConfiguration provideMailingListConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return MailingListConfiguration.from(propertiesProvider.getConfiguration("mailingLists"));
        } catch (FileNotFoundException e) {
            LOGGER.info("mailingLists.properties not found, using default mailing list configuration");
            return MailingListConfiguration.EMPTY;
        }
    }
}
