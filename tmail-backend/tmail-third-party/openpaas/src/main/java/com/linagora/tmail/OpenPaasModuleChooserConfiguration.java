package com.linagora.tmail;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.configuration.CardDavConfiguration;
import com.linagora.tmail.configuration.OpenPaasConfiguration;

public record OpenPaasModuleChooserConfiguration(boolean enabled,
                                                 boolean cardDavCollectedContactEnabled,
                                                 boolean contactsConsumerEnabled) {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenPaasModuleChooserConfiguration.class);
    public static final boolean ENABLED = true;
    public static final boolean DISABLED = false;
    public static final boolean ENABLE_CONTACTS_CONSUMER = true;
    public static final boolean ENABLE_CARDDAV = true;

    public static OpenPaasModuleChooserConfiguration parse(PropertiesProvider propertiesProvider) throws
        ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration("openpaas");
            boolean contactsConsumerEnabled = OpenPaasConfiguration.isConfiguredContactConsumer(configuration);
            boolean cardDavCollectedContactEnabled = CardDavConfiguration.isConfigured(configuration);
            LOGGER.info("OpenPaas module is turned on. Contacts consumer is enabled: {}, CardDav is enabled: {}",
                contactsConsumerEnabled, cardDavCollectedContactEnabled);
            return new OpenPaasModuleChooserConfiguration(ENABLED, cardDavCollectedContactEnabled, contactsConsumerEnabled);
        } catch (FileNotFoundException e) {
            LOGGER.info("OpenPaas module is turned off.");
            return new OpenPaasModuleChooserConfiguration(DISABLED, !ENABLE_CARDDAV, !ENABLE_CONTACTS_CONSUMER);
        }
    }
}
