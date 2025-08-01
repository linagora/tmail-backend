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
import org.apache.james.PostgresJamesConfiguration;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresConfiguration;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;
import org.apache.james.jmap.JMAPModule;
import org.apache.james.modules.queue.rabbitmq.MailQueueViewChoice;
import org.apache.james.server.core.JamesServerResourceLoader;
import org.apache.james.server.core.MissingArgumentException;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.ExtensionConfiguration;
import org.apache.james.utils.PropertiesProvider;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.UsersRepositoryModuleChooser;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.oidc.JMAPOidcConfiguration;
import com.linagora.tmail.james.jmap.oidc.OidcTokenCacheModuleChooser;
import com.linagora.tmail.james.jmap.service.discovery.LinagoraServicesDiscoveryModuleChooserConfiguration;

public record PostgresTmailConfiguration(ConfigurationPath configurationPath, JamesDirectoriesProvider directories,
                                         BlobStoreConfiguration blobStoreConfiguration,
                                         SearchConfiguration searchConfiguration,
                                         UsersRepositoryModuleChooser.Implementation usersRepositoryImplementation,
                                         MailQueueViewChoice mailQueueViewChoice,
                                         FirebaseModuleChooserConfiguration firebaseModuleChooserConfiguration,
                                         LinagoraServicesDiscoveryModuleChooserConfiguration linagoraServicesDiscoveryModuleChooserConfiguration,
                                         boolean jmapEnabled,
                                         boolean rlsEnabled,
                                         PropertiesProvider propertiesProvider,
                                         ExtensionConfiguration extentionConfiguration,
                                         PostgresJamesConfiguration.EventBusImpl eventBusImpl,
                                         boolean oidcEnabled,
                                         OidcTokenCacheModuleChooser.OidcTokenCacheChoice oidcTokenCacheChoice) implements Configuration {
    public static class Builder {
        private Optional<SearchConfiguration> searchConfiguration;
        private Optional<BlobStoreConfiguration> blobStoreConfiguration;
        private Optional<String> rootDirectory;
        private Optional<ConfigurationPath> configurationPath;
        private Optional<UsersRepositoryModuleChooser.Implementation> usersRepositoryImplementation;
        private Optional<MailQueueViewChoice> mailQueueViewChoice;
        private Optional<FirebaseModuleChooserConfiguration> firebaseModuleChooserConfiguration;
        private Optional<LinagoraServicesDiscoveryModuleChooserConfiguration> linagoraServicesDiscoveryModuleChooserConfiguration;
        private Optional<Boolean> jmapEnabled;
        private Optional<Boolean> rlsEnabled;
        private Optional<ExtensionConfiguration> extentionConfiguration;
        private Optional<PostgresJamesConfiguration.EventBusImpl> eventBusImpl;
        private Optional<Boolean> oidcEnabled;
        private Optional<OidcTokenCacheModuleChooser.OidcTokenCacheChoice> oidcTokenStorageChoice;

        private Builder() {
            searchConfiguration = Optional.empty();
            rootDirectory = Optional.empty();
            configurationPath = Optional.empty();
            blobStoreConfiguration = Optional.empty();
            usersRepositoryImplementation = Optional.empty();
            mailQueueViewChoice = Optional.empty();
            firebaseModuleChooserConfiguration = Optional.empty();
            linagoraServicesDiscoveryModuleChooserConfiguration = Optional.empty();
            jmapEnabled = Optional.empty();
            rlsEnabled = Optional.empty();
            extentionConfiguration = Optional.empty();
            eventBusImpl = Optional.empty();
            oidcEnabled = Optional.empty();
            oidcTokenStorageChoice = Optional.empty();
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

        public Builder blobStore(BlobStoreConfiguration blobStoreConfiguration) {
            this.blobStoreConfiguration = Optional.of(blobStoreConfiguration);
            return this;
        }

        public Builder searchConfiguration(SearchConfiguration searchConfiguration) {
            this.searchConfiguration = Optional.of(searchConfiguration);
            return this;
        }

        public Builder usersRepository(UsersRepositoryModuleChooser.Implementation implementation) {
            this.usersRepositoryImplementation = Optional.of(implementation);
            return this;
        }

        public Builder mailQueueViewChoice(MailQueueViewChoice mailQueueViewChoice) {
            this.mailQueueViewChoice = Optional.of(mailQueueViewChoice);
            return this;
        }

        public Builder firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration firebaseModuleChooserConfiguration) {
            this.firebaseModuleChooserConfiguration = Optional.of(firebaseModuleChooserConfiguration);
            return this;
        }

        public Builder linagoraServicesDiscoveryModuleChooserConfiguration(LinagoraServicesDiscoveryModuleChooserConfiguration servicesDiscoveryModuleChooserConfiguration) {
            this.linagoraServicesDiscoveryModuleChooserConfiguration = Optional.of(servicesDiscoveryModuleChooserConfiguration);
            return this;
        }

        public Builder jmapEnabled(boolean enable) {
            this.jmapEnabled = Optional.of(enable);
            return this;
        }

        public Builder rlsEnabled(Optional<Boolean> rlsEnabled) {
            this.rlsEnabled = rlsEnabled;
            return this;
        }

        public Builder extensionsConfiguration(ExtensionConfiguration configuration) {
            this.extentionConfiguration = Optional.of(configuration);
            return this;
        }

        public Builder eventBusImpl(PostgresJamesConfiguration.EventBusImpl eventBusImpl) {
            this.eventBusImpl = Optional.of(eventBusImpl);
            return this;
        }

        public Builder oidcEnabled(boolean enable) {
            this.oidcEnabled = Optional.of(enable);
            return this;
        }

        public Builder oidcTokenStorageChoice(OidcTokenCacheModuleChooser.OidcTokenCacheChoice oidcTokenCacheChoice) {
            this.oidcTokenStorageChoice = Optional.of(oidcTokenCacheChoice);
            return this;
        }

        public PostgresTmailConfiguration build() {
            ConfigurationPath configurationPath = this.configurationPath.orElse(new ConfigurationPath(FileSystem.FILE_PROTOCOL_AND_CONF));
            JamesServerResourceLoader directories = new JamesServerResourceLoader(rootDirectory
                .orElseThrow(() -> new MissingArgumentException("Server needs a working.directory env entry")));

            FileSystemImpl fileSystem = new FileSystemImpl(directories);
            PropertiesProvider propertiesProvider = new PropertiesProvider(fileSystem, configurationPath);
            BlobStoreConfiguration blobStoreConfiguration = this.blobStoreConfiguration.orElseGet(Throwing.supplier(
                () -> BlobStoreConfiguration.parse(propertiesProvider)));

            SearchConfiguration searchConfiguration = this.searchConfiguration.orElseGet(Throwing.supplier(
                () -> SearchConfiguration.parse(propertiesProvider)));

            FileConfigurationProvider configurationProvider = new FileConfigurationProvider(fileSystem, Basic.builder()
                .configurationPath(configurationPath)
                .workingDirectory(directories.getRootDirectory())
                .build());

            ExtensionConfiguration extentionConfiguration = this.extentionConfiguration.orElseGet(Throwing.supplier(
                () -> {
                    try {
                        return ExtensionConfiguration.from(new PropertiesProvider(fileSystem, configurationPath).getConfiguration("extensions"));
                    } catch (FileNotFoundException e) {
                        return ExtensionConfiguration.DEFAULT;
                    }
                }));

            UsersRepositoryModuleChooser.Implementation usersRepositoryChoice = usersRepositoryImplementation.orElseGet(
                () -> UsersRepositoryModuleChooser.Implementation.parse(configurationProvider));

            MailQueueViewChoice mailQueueViewChoice = this.mailQueueViewChoice.orElseGet(Throwing.supplier(
                () -> MailQueueViewChoice.parse(propertiesProvider)));

            FirebaseModuleChooserConfiguration firebaseModuleChooserConfiguration = this.firebaseModuleChooserConfiguration.orElseGet(Throwing.supplier(
                () -> FirebaseModuleChooserConfiguration.parse(propertiesProvider)));

            LinagoraServicesDiscoveryModuleChooserConfiguration servicesDiscoveryModuleChooserConfiguration = this.linagoraServicesDiscoveryModuleChooserConfiguration
                .orElseGet(Throwing.supplier(() -> LinagoraServicesDiscoveryModuleChooserConfiguration.parse(propertiesProvider)));

            boolean jmapEnabled = this.jmapEnabled.orElseGet(() -> {
                try {
                    return JMAPModule.parseConfiguration(propertiesProvider).isEnabled();
                } catch (FileNotFoundException e) {
                    return false;
                } catch (ConfigurationException e) {
                    throw new RuntimeException(e);
                }
            });

            boolean rlsEnabled = this.rlsEnabled.orElse(readRLSEnabledFromFile(propertiesProvider));

            PostgresJamesConfiguration.EventBusImpl eventBusImpl = this.eventBusImpl.orElseGet(() -> PostgresJamesConfiguration.EventBusImpl.from(propertiesProvider));

            boolean oidcEnabled = this.oidcEnabled.orElseGet(() -> {
                try {
                    return JMAPOidcConfiguration.parseConfiguration(propertiesProvider).getOidcEnabled();
                } catch (FileNotFoundException e) {
                    return false;
                } catch (ConfigurationException e) {
                    throw new RuntimeException(e);
                }
            });

            OidcTokenCacheModuleChooser.OidcTokenCacheChoice oidcTokenCacheChoice = this.oidcTokenStorageChoice.orElseGet(Throwing.supplier(
                () -> OidcTokenCacheModuleChooser.OidcTokenCacheChoice.from(propertiesProvider)));

            return new PostgresTmailConfiguration(
                configurationPath,
                directories,
                blobStoreConfiguration,
                searchConfiguration,
                usersRepositoryChoice,
                mailQueueViewChoice,
                firebaseModuleChooserConfiguration,
                servicesDiscoveryModuleChooserConfiguration,
                jmapEnabled,
                rlsEnabled,
                propertiesProvider,
                extentionConfiguration,
                eventBusImpl,
                oidcEnabled,
                oidcTokenCacheChoice);
        }

        private boolean readRLSEnabledFromFile(PropertiesProvider propertiesProvider) {
            try {
                return PostgresConfiguration.from(propertiesProvider.getConfiguration(PostgresConfiguration.POSTGRES_CONFIGURATION_NAME)).getRowLevelSecurity().isRowLevelSecurityEnabled();
            } catch (FileNotFoundException | ConfigurationException e) {
                return false;
            }
        }
    }

    public static PostgresTmailConfiguration.Builder builder() {
        return new Builder();
    }

    public boolean hasConfigurationProperties(String configurationPropertiesName) {
        try {
            propertiesProvider().getConfiguration(configurationPropertiesName);
            return true;
        } catch (FileNotFoundException notFoundException) {
            return false;
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }
}
