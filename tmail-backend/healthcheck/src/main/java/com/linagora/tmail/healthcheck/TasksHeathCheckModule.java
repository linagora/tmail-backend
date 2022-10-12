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
