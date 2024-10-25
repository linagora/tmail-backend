package com.linagora.tmail;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record OpenPaasModuleChooserConfiguration(boolean enabled) {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenPaasModuleChooserConfiguration.class);
    public static final OpenPaasModuleChooserConfiguration ENABLED = new OpenPaasModuleChooserConfiguration(true);
    public static final OpenPaasModuleChooserConfiguration DISABLED = new OpenPaasModuleChooserConfiguration(false);

    public static OpenPaasModuleChooserConfiguration parse(PropertiesProvider propertiesProvider) throws
        ConfigurationException {
        try {
            propertiesProvider.getConfiguration("openpaas");
            LOGGER.info("OpenPaas module is turned on.");
            return ENABLED;
        } catch (FileNotFoundException e) {
            LOGGER.info("OpenPaas module is turned off.");
            return DISABLED;
        }
    }
}
