package com.linagora.openpaas.james.app;

import java.io.File;
import java.util.Optional;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;
import org.apache.james.server.core.JamesServerResourceLoader;
import org.apache.james.server.core.MissingArgumentException;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.PropertiesProvider;

import com.github.fge.lambdas.Throwing;
import com.linagora.openpaas.encrypted.MailboxConfiguration;

public class MemoryConfiguration implements Configuration {
    public static class Builder {
        private Optional<MailboxConfiguration> mailboxConfiguration;
        private Optional<String> rootDirectory;
        private Optional<ConfigurationPath> configurationPath;

        private Builder() {
            mailboxConfiguration = Optional.empty();
            rootDirectory = Optional.empty();
            configurationPath = Optional.empty();
        }

        public Builder workingDirectory(String path) {
            rootDirectory = Optional.of(path);
            return this;
        }

        public Builder workingDirectory(File file) {
            rootDirectory = Optional.of(file.getAbsolutePath());
            return this;
        }

        public Builder useWorkingDirectoryEnvProperty() {
            rootDirectory = Optional.ofNullable(System.getProperty(WORKING_DIRECTORY));
            if (!rootDirectory.isPresent()) {
                throw new MissingArgumentException("Server needs a working.directory env entry");
            }
            return this;
        }

        public Builder configurationPath(ConfigurationPath path) {
            configurationPath = Optional.of(path);
            return this;
        }

        public Builder configurationFromClasspath() {
            configurationPath = Optional.of(new ConfigurationPath(FileSystem.CLASSPATH_PROTOCOL));
            return this;
        }

        public Builder mailbox(MailboxConfiguration mailboxConfiguration) {
            this.mailboxConfiguration = Optional.of(mailboxConfiguration);
            return this;
        }


        public MemoryConfiguration build() {
            ConfigurationPath configurationPath = this.configurationPath.orElse(new ConfigurationPath(FileSystem.FILE_PROTOCOL_AND_CONF));
            JamesServerResourceLoader directories = new JamesServerResourceLoader(rootDirectory
                .orElseThrow(() -> new MissingArgumentException("Server needs a working.directory env entry")));

            FileSystemImpl fileSystem = new FileSystemImpl(directories);

            MailboxConfiguration mailboxConfiguration = this.mailboxConfiguration.orElseGet(Throwing.supplier(
                () -> MailboxConfiguration.parse(
                    new PropertiesProvider(fileSystem, configurationPath))));

            FileConfigurationProvider configurationProvider = new FileConfigurationProvider(fileSystem, Basic.builder()
                .configurationPath(configurationPath)
                .workingDirectory(directories.getRootDirectory())
                .build());

            return new MemoryConfiguration(
                configurationPath,
                directories,
                mailboxConfiguration);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final ConfigurationPath configurationPath;
    private final JamesDirectoriesProvider directories;
    private final MailboxConfiguration mailboxConfiguration;

    public MemoryConfiguration(ConfigurationPath configurationPath, JamesDirectoriesProvider directories, MailboxConfiguration mailboxConfiguration) {
        this.configurationPath = configurationPath;
        this.directories = directories;
        this.mailboxConfiguration = mailboxConfiguration;
    }

    @Override
    public ConfigurationPath configurationPath() {
        return configurationPath;
    }

    @Override
    public JamesDirectoriesProvider directories() {
        return directories;
    }

    public MailboxConfiguration mailboxConfiguration() {
        return mailboxConfiguration;
    }
}
