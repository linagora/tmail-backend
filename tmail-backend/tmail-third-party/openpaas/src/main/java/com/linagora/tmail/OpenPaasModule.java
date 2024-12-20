package com.linagora.tmail;

import java.io.FileNotFoundException;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.linagora.tmail.api.OpenPaasRestClient;
import com.linagora.tmail.carddav.CardDavClient;
import com.linagora.tmail.configuration.OpenPaasConfiguration;

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
    public OpenPaasRestClient provideOpenPaasRestCLient(OpenPaasConfiguration openPaasConfiguration) {
        return new OpenPaasRestClient(openPaasConfiguration);
    }

    public static class CardDavModule extends AbstractModule {

        @Provides
        @Singleton
        public CardDavClient provideCardDavClient(OpenPaasConfiguration openPaasConfiguration) {
            Preconditions.checkArgument(openPaasConfiguration.cardDavConfiguration().isPresent(),
                "OpenPaasConfiguration should have a carddav configuration");
            return new CardDavClient.OpenpaasCardDavClient(openPaasConfiguration.cardDavConfiguration().get());
        }
    }
}
