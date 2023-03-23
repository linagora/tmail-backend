package com.linagora.tmail.encrypted;

import java.io.FileNotFoundException;
import java.util.Objects;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.PropertiesProvider;

public class MailboxConfiguration {
    public static MailboxConfiguration parse(org.apache.james.server.core.configuration.Configuration configuration) throws ConfigurationException {
        PropertiesProvider propertiesProvider = new PropertiesProvider(new FileSystemImpl(configuration.directories()),
            configuration.configurationPath());

        return parse(propertiesProvider);
    }

    public static MailboxConfiguration parse(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration("mailbox");
            return new MailboxConfiguration(configuration.getBoolean("gpg.encryption.enable", false));
        } catch (FileNotFoundException e) {
            return new MailboxConfiguration(false);
        }
    }

    private final boolean enableEncryption;

    public MailboxConfiguration(boolean enableEncryption) {
        this.enableEncryption = enableEncryption;
    }

    public boolean isEncryptionEnabled() {
        return enableEncryption;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(enableEncryption);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MailboxConfiguration other) {
            return other.enableEncryption == this.enableEncryption;
        }
        return false;
    }
}
