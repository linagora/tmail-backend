package com.linagora.tmail.james.jmap.firebase;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.utils.PropertiesProvider;

public record FirebaseModuleChooserConfiguration(boolean enable) {
    public static final FirebaseModuleChooserConfiguration ENABLED = new FirebaseModuleChooserConfiguration(true);
    public static final FirebaseModuleChooserConfiguration DISABLED = new FirebaseModuleChooserConfiguration(false);

    public static FirebaseModuleChooserConfiguration parse(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration("firebase");
            return new FirebaseModuleChooserConfiguration(configuration.getBoolean("enable", true));
        } catch (FileNotFoundException e) {
            return new FirebaseModuleChooserConfiguration(false);
        }
    }
}
