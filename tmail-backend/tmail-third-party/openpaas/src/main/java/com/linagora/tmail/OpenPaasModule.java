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
import java.util.function.Function;

import javax.net.ssl.SSLException;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.linagora.tmail.api.OpenPaasRestClient;
import com.linagora.tmail.configuration.OpenPaasConfiguration;
import com.linagora.tmail.dav.CalDavEventRepository;
import com.linagora.tmail.dav.CardDavAddContactProcessor;
import com.linagora.tmail.dav.DavClient;
import com.linagora.tmail.dav.DavUserProvider;
import com.linagora.tmail.dav.OpenPaasDavUserProvider;
import com.linagora.tmail.james.jmap.CalendarEventRepository;
import com.linagora.tmail.james.jmap.EventAttendanceRepository;
import com.linagora.tmail.james.jmap.calendar.CalendarResolver;
import com.linagora.tmail.james.jmap.contact.ContactAddIndexingProcessor;

public class OpenPaasModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenPaasModule.class);
    public static final String OPENPAAS_INJECTION_KEY = "openpaas";
    public static final String OPENPAAS_CONFIGURATION_NAME = "openpaas";

    @Provides
    @Named(OPENPAAS_CONFIGURATION_NAME)
    @Singleton
    public Configuration providePropertiesConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return propertiesProvider.getConfiguration(OPENPAAS_CONFIGURATION_NAME);
        } catch (FileNotFoundException e) {
            LOGGER.error("Could not find configuration file '{}.properties'",
                OPENPAAS_CONFIGURATION_NAME);
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Singleton
    public OpenPaasConfiguration provideOpenPaasConfiguration(@Named(OPENPAAS_CONFIGURATION_NAME) Configuration propertiesConfiguration) {
        return OpenPaasConfiguration.from(propertiesConfiguration);
    }

    @Provides
    public OpenPaasRestClient provideOpenPaasRestCLient(OpenPaasConfiguration openPaasConfiguration) throws SSLException {
        return new OpenPaasRestClient(openPaasConfiguration);
    }

    public static class DavModule extends AbstractModule {

        public static final Boolean CALDAV_SUPPORTED = true;

        public static final Function<Boolean, Module> CALDAV_SUPPORT_MODULE_PROVIDER =
            calDavSupport -> new AbstractModule() {
                @Provides
                @Singleton
                @Named("calDavSupport")
                Boolean provideCalDavSupport() {
                    return calDavSupport;
                }
            };

        @Override
        protected void configure() {
            bind(ContactAddIndexingProcessor.class).to(CardDavAddContactProcessor.class)
                .in(Scopes.SINGLETON);
        }

        @Provides
        @Singleton
        public DavClient provideDavClient(OpenPaasConfiguration openPaasConfiguration) throws SSLException {
            Preconditions.checkArgument(openPaasConfiguration.davConfiguration().isPresent(),
                "OpenPaasConfiguration should have dav configuration");
            return new DavClient(openPaasConfiguration.davConfiguration().get());
        }

        @Provides
        @Singleton
        public CalendarEventRepository provideCalDavEventRepository(DavClient davClient,
                                                                    SessionProvider sessionProvider,
                                                                    DavUserProvider davUserProvider,
                                                                    CalendarResolver calendarResolver) {
            return new CalDavEventRepository(davClient, sessionProvider, davUserProvider, calendarResolver);
        }

        @Provides
        @Singleton
        public EventAttendanceRepository provideCalDavEventAttendanceRepository(CalendarEventRepository calendarEventRepository) {
            return calendarEventRepository;
        }

        @Provides
        @Singleton
        public DavUserProvider provideDavUserProvider(OpenPaasRestClient openPaasClient, DavClient davClient) {
            return new OpenPaasDavUserProvider(openPaasClient, davClient);
        }
    }
}
