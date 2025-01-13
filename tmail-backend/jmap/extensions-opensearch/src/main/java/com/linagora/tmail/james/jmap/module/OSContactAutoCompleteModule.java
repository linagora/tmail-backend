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

package com.linagora.tmail.james.jmap.module;

import java.io.FileNotFoundException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.opensearch.OpenSearchConfiguration;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.james.jmap.OSEmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.OpenSearchContactConfiguration;
import com.linagora.tmail.james.jmap.contact.ContactUserDeletionTaskStep;
import com.linagora.tmail.james.jmap.contact.ContactUsernameChangeTaskStep;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.contact.MinAutoCompleteInputLength;

public class OSContactAutoCompleteModule extends AbstractModule {
    public static final String OPENSEARCH_CONFIGURATION_NAME = "opensearch";

    private static final Logger LOGGER = LoggerFactory.getLogger(OSContactAutoCompleteModule.class);

    static class ContactIndexCreator implements Startable {

        private final OpenSearchConfiguration openSearchConfiguration;
        private final OpenSearchContactConfiguration contactConfiguration;
        private final ReactorOpenSearchClient client;

        @Inject
        ContactIndexCreator(OpenSearchConfiguration configuration,
                            OpenSearchContactConfiguration contactConfiguration,
                            ReactorOpenSearchClient client) {
            this.openSearchConfiguration = configuration;
            this.contactConfiguration = contactConfiguration;
            this.client = client;
        }

        void createIndex() {
            ContactIndexCreationUtil.createIndices(client, openSearchConfiguration, contactConfiguration);
        }
    }

    @Override
    protected void configure() {
        bind(OSEmailAddressContactSearchEngine.class).in(Scopes.SINGLETON);

        bind(EmailAddressContactSearchEngine.class).to(OSEmailAddressContactSearchEngine.class);

        Multibinder.newSetBinder(binder(), UsernameChangeTaskStep.class)
            .addBinding()
            .to(ContactUsernameChangeTaskStep.class);
        Multibinder.newSetBinder(binder(), DeleteUserDataTaskStep.class)
            .addBinding()
            .to(ContactUserDeletionTaskStep.class);
    }

    @Provides
    @Singleton
    private OpenSearchContactConfiguration getOpenSearchContactConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration(OPENSEARCH_CONFIGURATION_NAME);
            return OpenSearchContactConfiguration.fromProperties(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + OPENSEARCH_CONFIGURATION_NAME + " configuration file. Using default contact configuration");
            return OpenSearchContactConfiguration.DEFAULT_CONFIGURATION;
        }
    }

    @Provides
    @Singleton
    private MinAutoCompleteInputLength provideMinInputLength(OpenSearchContactConfiguration openSearchContactConfiguration) {
        return MinAutoCompleteInputLength.apply(openSearchContactConfiguration.getMinNgram());
    }

    @ProvidesIntoSet
    InitializationOperation createIndex(ContactIndexCreator instance) {
        return InitilizationOperationBuilder
            .forClass(ContactIndexCreator.class)
            .init(instance::createIndex);
    }
}
