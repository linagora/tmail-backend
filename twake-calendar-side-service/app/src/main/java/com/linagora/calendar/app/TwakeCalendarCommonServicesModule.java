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

package com.linagora.calendar.app;

import jakarta.inject.Singleton;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;
import org.apache.james.modules.CleanupTaskModule;
import org.apache.james.modules.ClockModule;
import org.apache.james.modules.PeriodicalHealthChecksModule;
import org.apache.james.modules.RunArgumentsModule;
import org.apache.james.modules.StartUpChecksModule;
import org.apache.james.modules.StartablesModule;
import org.apache.james.modules.server.DropWizardMetricsModule;
import org.apache.james.onami.lifecycle.PreDestroyModule;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.ExtensionModule;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class TwakeCalendarCommonServicesModule extends AbstractModule {

    private final Configuration configuration;
    private final FileSystemImpl fileSystem;

    public TwakeCalendarCommonServicesModule(Configuration configuration) {
        this.configuration = configuration;
        this.fileSystem = new FileSystemImpl(configuration.directories());

    }
    
    @Override
    protected void configure() {
        install(new ExtensionModule());
        install(new StartUpChecksModule());
        install(new StartablesModule());
        install(new PreDestroyModule());
        install(new DropWizardMetricsModule());
        install(new CleanupTaskModule());
        install(new ClockModule());
        install(new PeriodicalHealthChecksModule());
        install(RunArgumentsModule.EMPTY);

        bind(FileSystem.class).toInstance(fileSystem);
        bind(Configuration.class).toInstance(configuration);

        bind(ConfigurationProvider.class).toInstance(new FileConfigurationProvider(fileSystem, configuration));
    }

    @Provides
    @Singleton
    public Configuration.ConfigurationPath configurationPath() {
        return configuration.configurationPath();
    }

    @Provides
    @Singleton
    public JamesDirectoriesProvider directories() {
        return configuration.directories();
    }

    @Provides
    @Singleton
    public PropertiesProvider providePropertiesProvider(FileSystem fileSystem, Configuration.ConfigurationPath configurationPrefix) {
        return new PropertiesProvider(fileSystem, configurationPrefix);
    }
}
