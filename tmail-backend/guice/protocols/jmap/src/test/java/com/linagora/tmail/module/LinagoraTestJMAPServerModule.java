package com.linagora.tmail.module;

import jakarta.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.modules.TestJMAPServerModule;

import com.google.inject.Provides;
import com.google.inject.name.Named;

public class LinagoraTestJMAPServerModule extends TestJMAPServerModule {
    @Provides
    @Singleton
    @Named("jmap")
    Configuration provideConfiguration() {
        Configuration configuration = new PropertiesConfiguration();
        configuration.addProperty("calendarEvent.reply.mailTemplateLocation", "classpath://eml/");
        return configuration;
    }
}