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

package com.linagora.tmail.blob.guice;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.linagora.tmail.blob.mail.DuplicatingMimeMessageStore;
import com.linagora.tmail.blob.mail.MailProcessingConfiguration;

/**
 * Substitutes James' {@link MimeMessageStore.Factory} - injected by the mail queue and by the mail
 * repositories - with the Twake Mail one, allowing mails in transit not to be deduplicated.
 *
 * @see MailProcessingConfiguration
 */
public class MailProcessingModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailProcessingModule.class);

    @Override
    protected void configure() {
        bind(DuplicatingMimeMessageStore.Factory.class).in(Scopes.SINGLETON);
        bind(MimeMessageStore.Factory.class).to(DuplicatingMimeMessageStore.Factory.class);
    }

    @Provides
    @Singleton
    MailProcessingConfiguration mailProcessingConfiguration(PropertiesProvider propertiesProvider) {
        try {
            return MailProcessingConfiguration.from(propertiesProvider.getConfigurations(ConfigurationComponent.NAMES));
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find {} configuration file, deduplicating mails in transit", ConfigurationComponent.NAME);
            return MailProcessingConfiguration.DEDUPLICATED;
        } catch (ConfigurationException e) {
            throw new RuntimeException("Failed reading " + ConfigurationComponent.NAME + " configuration file", e);
        }
    }
}
