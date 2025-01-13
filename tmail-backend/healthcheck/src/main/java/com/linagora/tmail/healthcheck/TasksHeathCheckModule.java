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

package com.linagora.tmail.healthcheck;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class TasksHeathCheckModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(TasksHeathCheckModule.class);
    private static final String FILENAME = "healthcheck";

    @Override
    protected void configure() {
        Multibinder<HealthCheck> healthCheckMultibinder = Multibinder.newSetBinder(binder(), HealthCheck.class);
        healthCheckMultibinder.addBinding().to(TasksHeathCheck.class);
    }

    @Singleton
    @Provides
    TasksHealthCheckConfiguration tasksHealthCheckConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfigurations(FILENAME);
            return TasksHealthCheckConfiguration.from(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find {} configuration file, using default tasks healthcheck configuration", FILENAME);
            return TasksHealthCheckConfiguration.DEFAULT_CONFIGURATION;
        }
    }
}
