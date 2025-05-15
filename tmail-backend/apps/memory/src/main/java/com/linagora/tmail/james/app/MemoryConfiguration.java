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

package com.linagora.tmail.james.app;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Optional;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.data.UsersRepositoryModuleChooser;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;
import org.apache.james.jmap.JMAPModule;
import org.apache.james.server.core.JamesServerResourceLoader;
import org.apache.james.server.core.MissingArgumentException;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.ExtendedClassLoader;
import org.apache.james.utils.ExtensionConfiguration;
import org.apache.james.utils.PropertiesProvider;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.OpenPaasModuleChooserConfiguration;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.service.discovery.LinagoraServicesDiscoveryModuleChooserConfiguration;

public record MemoryConfiguration(ConfigurationPath configurationPath, JamesDirectoriesProvider directories,
                                  MailboxConfiguration mailboxConfiguration,
                                  UsersRepositoryModuleChooser.Implementation usersRepositoryImplementation,
                                  FirebaseModuleChooserConfiguration firebaseModuleChooserConfiguration,
                                  LinagoraServicesDiscoveryModuleChooserConfiguration linagoraServicesDiscoveryModuleChooserConfiguration,
                                  OpenPaasModuleChooserConfiguration openPaasModuleChooserConfiguration,
                                  FileConfigurationProvider fileConfigurationProvider,
                                  ExtensionConfiguration extentionConfiguration,
                                  ExtendedClassLoader extendedClassLoader,
                                  boolean jmapEnabled,
                                  boolean dropListEnabled) implements Configuration {
    public static class Builder {
        private Optional<MailboxConfiguration> mailboxConfiguration;
        private Optional<String> rootDirectory;
        private Optional<ConfigurationPath> configurationPath;
        private Optional<UsersRepositoryModuleChooser.Implementation> usersRepositoryImplementation;
        private Optional<FirebaseModuleChooserConfiguration> firebaseModuleChooserConfiguration;
        private Optional<LinagoraServicesDiscoveryModuleChooserConfiguration> linagoraServicesDiscoveryModuleChooserConfiguration;
        private Optional<OpenPaasModuleChooserConfiguration> openPaasModuleChooserConfiguration;
        private Optional<ExtensionConfiguration> extentionConfiguration;
        private Optional<ExtendedClassLoader> extendedClassLoader;
        private Optional<Boolean> jmapEnabled;
        private Optional<Boolean> dropListsEnabled;


        private Builder() {
            mailboxConfiguration = Optional.empty();
            rootDirectory = Optional.empty();
            configurationPath = Optional.empty();
            usersRepositoryImplementation = Optional.empty();
            firebaseModuleChooserConfiguration = Optional.empty();
            linagoraServicesDiscoveryModuleChooserConfiguration = Optional.empty();
            openPaasModuleChooserConfiguration = Optional.empty();
            extentionConfiguration = Optional.empty();
            extendedClassLoader = Optional.empty();
            jmapEnabled = Optional.empty();
            dropListsEnabled = Optional.empty();
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
            if (rootDirectory.isEmpty()) {
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

        public Builder extensionsConfiguration(ExtensionConfiguration configuration) {
            this.extentionConfiguration = Optional.of(configuration);
            return this;
        }

        public Builder extendedClassLoader(ExtendedClassLoader extendedClassLoader) {
            this.extendedClassLoader = Optional.of(extendedClassLoader);
            return this;
        }

        public Builder usersRepository(UsersRepositoryModuleChooser.Implementation implementation) {
            this.usersRepositoryImplementation = Optional.of(implementation);
            return this;
        }

        public Builder firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration firebaseModuleChooserConfiguration) {
            this.firebaseModuleChooserConfiguration = Optional.of(firebaseModuleChooserConfiguration);
            return this;
        }

        public Builder linagoraServiceDiscoveryModuleChooserConfiguration(LinagoraServicesDiscoveryModuleChooserConfiguration servicesDiscoveryModuleChooserConfiguration) {
            this.linagoraServicesDiscoveryModuleChooserConfiguration = Optional.of(servicesDiscoveryModuleChooserConfiguration);
            return this;
        }

        public Builder openPaasModuleChooserConfiguration(OpenPaasModuleChooserConfiguration openPaasModuleChooserConfiguration) {
            this.openPaasModuleChooserConfiguration = Optional.of(openPaasModuleChooserConfiguration);
            return this;
        }

        public Builder jmapEnabled(boolean enable) {
            this.jmapEnabled = Optional.of(enable);
            return this;
        }

        public Builder enableDropLists() {
            this.dropListsEnabled = Optional.of(true);
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

            ExtensionConfiguration extentionConfiguration = this.extentionConfiguration.orElseGet(Throwing.supplier(
                () -> ExtensionConfiguration.from(new PropertiesProvider(fileSystem, configurationPath).getConfiguration("extensions"))));

            ExtendedClassLoader extendedClassLoader = this.extendedClassLoader.orElseGet(() ->
                new ExtendedClassLoader(fileSystem));

            FileConfigurationProvider configurationProvider = new FileConfigurationProvider(fileSystem, Basic.builder()
                .configurationPath(configurationPath)
                .workingDirectory(directories.getRootDirectory())
                .build());

            UsersRepositoryModuleChooser.Implementation usersRepositoryChoice = usersRepositoryImplementation.orElseGet(
                () -> UsersRepositoryModuleChooser.Implementation.parse(configurationProvider));

            FirebaseModuleChooserConfiguration firebaseModuleChooserConfiguration = this.firebaseModuleChooserConfiguration.orElseGet(Throwing.supplier(
                () -> FirebaseModuleChooserConfiguration.parse(new PropertiesProvider(fileSystem, configurationPath))));

            LinagoraServicesDiscoveryModuleChooserConfiguration servicesDiscoveryModuleChooserConfiguration = this.linagoraServicesDiscoveryModuleChooserConfiguration.orElseGet(Throwing.supplier(
                () -> LinagoraServicesDiscoveryModuleChooserConfiguration.parse(new PropertiesProvider(fileSystem, configurationPath))));

            OpenPaasModuleChooserConfiguration openPaasModuleChooserConfiguration = this.openPaasModuleChooserConfiguration.orElseGet(Throwing.supplier(
                () -> OpenPaasModuleChooserConfiguration.parse(new PropertiesProvider(fileSystem, configurationPath))));

            boolean jmapEnabled = this.jmapEnabled.orElseGet(() -> {
                PropertiesProvider propertiesProvider = new PropertiesProvider(fileSystem, configurationPath);
                try {
                    return JMAPModule.parseConfiguration(propertiesProvider).isEnabled();
                } catch (FileNotFoundException e) {
                    return false;
                } catch (ConfigurationException e) {
                    throw new RuntimeException(e);
                }
            });

            boolean dropListsEnabled = this.dropListsEnabled.orElseGet(() -> {
                PropertiesProvider propertiesProvider = new PropertiesProvider(fileSystem, configurationPath);
                try {
                    return propertiesProvider.getConfiguration("droplists").getBoolean("enabled", false);
                } catch (FileNotFoundException e) {
                    return false;
                } catch (ConfigurationException e) {
                    throw new RuntimeException(e);
                }
            });

            return new MemoryConfiguration(
                configurationPath,
                directories,
                mailboxConfiguration,
                usersRepositoryChoice,
                firebaseModuleChooserConfiguration,
                servicesDiscoveryModuleChooserConfiguration,
                openPaasModuleChooserConfiguration,
                configurationProvider,
                extentionConfiguration,
                extendedClassLoader,
                jmapEnabled,
                dropListsEnabled);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

}
