package com.linagora.tmail.module;

import java.io.FileNotFoundException;

import jakarta.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.Provides;
import com.google.inject.name.Named;

public class LinagoraTestJMAPServerModule extends TestJMAPServerModule {
    @Provides
    @Singleton
    @Named("jmap")
    Configuration provideConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return propertiesProvider.getConfiguration("jmap");
        } catch (FileNotFoundException e) {
            // return a default configuration for the test
            Configuration configuration = new PropertiesConfiguration();
            configuration.addProperty("calendarEvent.reply.mailTemplateLocation", "classpath://eml/");
            configuration.addProperty("url.prefix", "http://localhost:8000");
            return configuration;
        }
    }
}