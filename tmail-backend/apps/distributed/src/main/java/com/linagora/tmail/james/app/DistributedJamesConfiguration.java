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
import org.apache.james.SearchConfiguration;
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
import org.apache.james.vault.VaultConfiguration;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.OpenPaasModuleChooserConfiguration;
import com.linagora.tmail.UsersRepositoryModuleChooser;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.oidc.JMAPOidcConfiguration;
import com.linagora.tmail.james.jmap.oidc.OidcTokenCacheModuleChooser;
import com.linagora.tmail.james.jmap.service.discovery.LinagoraServicesDiscoveryModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.settings.TWPSettingsModuleChooserConfiguration;

public record DistributedJamesConfiguration(ConfigurationPath configurationPath, JamesDirectoriesProvider directories,
                                            MailboxConfiguration mailboxConfiguration,
                                            BlobStoreConfiguration blobStoreConfiguration,
                                            SearchConfiguration searchConfiguration,
                                            UsersRepositoryModuleChooser.Implementation usersRepositoryImplementation,
                                            MailQueueViewChoice mailQueueViewChoice,
                                            FirebaseModuleChooserConfiguration firebaseModuleChooserConfiguration,
                                            LinagoraServicesDiscoveryModuleChooserConfiguration linagoraServicesDiscoveryModuleChooserConfiguration,
                                            OpenPaasModuleChooserConfiguration openPaasModuleChooserConfiguration,
                                            TWPSettingsModuleChooserConfiguration twpSettingsModuleChooserConfiguration,
                                            boolean jmapEnabled,
                                            PropertiesProvider propertiesProvider,
                                            FileConfigurationProvider fileConfigurationProvider,
                                            boolean quotaCompatibilityMode,
                                            EventBusKeysChoice eventBusKeysChoice,
                                            boolean dropListEnabled,
                                            VaultConfiguration vaultConfiguration,
                                            ExtensionConfiguration extentionConfiguration,
                                            boolean oidcEnabled,
                                            OidcTokenCacheModuleChooser.OidcTokenCacheChoice oidcTokenCacheChoice) implements Configuration {
    public static class Builder {
        private Optional<MailboxConfiguration> mailboxConfiguration;
        private Optional<SearchConfiguration> searchConfiguration;
        private Optional<BlobStoreConfiguration> blobStoreConfiguration;
        private Optional<String> rootDirectory;
        private Optional<ConfigurationPath> configurationPath;
        private Optional<UsersRepositoryModuleChooser.Implementation> usersRepositoryImplementation;
        private Optional<MailQueueViewChoice> mailQueueViewChoice;
        private Optional<FirebaseModuleChooserConfiguration> firebaseModuleChooserConfiguration;
        private Optional<LinagoraServicesDiscoveryModuleChooserConfiguration> linagoraServicesDiscoveryModuleChooserConfiguration;
        private Optional<OpenPaasModuleChooserConfiguration> openPaasModuleChooserConfiguration;
        private Optional<TWPSettingsModuleChooserConfiguration> twpSettingsModuleChooserConfiguration;
        private Optional<Boolean> jmapEnabled;
        private Optional<EventBusKeysChoice> eventBusKeysChoice;
        private Optional<Boolean> quotaCompatibilityMode;
        private Optional<Boolean> dropListsEnabled;
        private Optional<VaultConfiguration> vaultConfiguration;
        private Optional<ExtensionConfiguration> extentionConfiguration;
        private Optional<Boolean> oidcEnabled;
        private Optional<OidcTokenCacheModuleChooser.OidcTokenCacheChoice> oidcTokenStorageChoice;

        private Builder() {
            searchConfiguration = Optional.empty();
            mailboxConfiguration = Optional.empty();
            rootDirectory = Optional.empty();
            configurationPath = Optional.empty();
            blobStoreConfiguration = Optional.empty();
            usersRepositoryImplementation = Optional.empty();
            mailQueueViewChoice = Optional.empty();
            firebaseModuleChooserConfiguration = Optional.empty();
            linagoraServicesDiscoveryModuleChooserConfiguration = Optional.empty();
            openPaasModuleChooserConfiguration = Optional.empty();
            twpSettingsModuleChooserConfiguration = Optional.empty();
            jmapEnabled = Optional.empty();
            quotaCompatibilityMode = Optional.empty();
            eventBusKeysChoice = Optional.empty();
            dropListsEnabled = Optional.empty();
            vaultConfiguration = Optional.empty();
            extentionConfiguration = Optional.empty();
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

        public Builder mailbox(MailboxConfiguration mailboxConfiguration) {
            this.mailboxConfiguration = Optional.of(mailboxConfiguration);
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

        public Builder openPassModuleChooserConfiguration(OpenPaasModuleChooserConfiguration openPaasModuleChooserConfiguration) {
            this.openPaasModuleChooserConfiguration = Optional.of(openPaasModuleChooserConfiguration);
            return this;
        }

        public Builder twpSettingsModuleChooserConfiguration(TWPSettingsModuleChooserConfiguration twpSettingsModuleChooserConfiguration) {
            this.twpSettingsModuleChooserConfiguration = Optional.of(twpSettingsModuleChooserConfiguration);
            return this;
        }

        public Builder jmapEnabled(boolean enable) {
            this.jmapEnabled = Optional.of(enable);
            return this;
        }

        public Builder quotaCompatibilityMode(boolean enable) {
            this.quotaCompatibilityMode = Optional.of(enable);
            return this;
        }

        public Builder eventBusKeysChoice(EventBusKeysChoice eventBusKeysChoice) {
            this.eventBusKeysChoice = Optional.of(eventBusKeysChoice);
            return this;
        }

        public Builder enableDropLists() {
            this.dropListsEnabled = Optional.of(true);
            return this;
        }

        public Builder vaultConfiguration(VaultConfiguration vaultConfiguration) {
            this.vaultConfiguration = Optional.of(vaultConfiguration);
            return this;
        }

        public Builder extensionsConfiguration(ExtensionConfiguration configuration) {
            this.extentionConfiguration = Optional.of(configuration);
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

        public DistributedJamesConfiguration build() {
            ConfigurationPath configurationPath = this.configurationPath.orElse(new ConfigurationPath(FileSystem.FILE_PROTOCOL_AND_CONF));
            JamesServerResourceLoader directories = new JamesServerResourceLoader(rootDirectory
                .orElseThrow(() -> new MissingArgumentException("Server needs a working.directory env entry")));

            FileSystemImpl fileSystem = new FileSystemImpl(directories);
            PropertiesProvider propertiesProvider = new PropertiesProvider(fileSystem, configurationPath);
            BlobStoreConfiguration blobStoreConfiguration = this.blobStoreConfiguration.orElseGet(Throwing.supplier(
                () -> BlobStoreConfiguration.parse(propertiesProvider)));

            SearchConfiguration searchConfiguration = this.searchConfiguration.orElseGet(Throwing.supplier(
                () -> SearchConfiguration.parse(propertiesProvider)));

            MailboxConfiguration mailboxConfiguration = this.mailboxConfiguration.orElseGet(Throwing.supplier(
                () -> MailboxConfiguration.parse(propertiesProvider)));

            FileConfigurationProvider configurationProvider = new FileConfigurationProvider(fileSystem, Basic.builder()
                .configurationPath(configurationPath)
                .workingDirectory(directories.getRootDirectory())
                .build());
            UsersRepositoryModuleChooser.Implementation usersRepositoryChoice = usersRepositoryImplementation.orElseGet(
                () -> UsersRepositoryModuleChooser.Implementation.parse(configurationProvider));

            MailQueueViewChoice mailQueueViewChoice = this.mailQueueViewChoice.orElseGet(Throwing.supplier(
                () -> MailQueueViewChoice.parse(propertiesProvider)));

            FirebaseModuleChooserConfiguration firebaseModuleChooserConfiguration = this.firebaseModuleChooserConfiguration.orElseGet(Throwing.supplier(
                () -> FirebaseModuleChooserConfiguration.parse(propertiesProvider)));

            LinagoraServicesDiscoveryModuleChooserConfiguration servicesDiscoveryModuleChooserConfiguration = this.linagoraServicesDiscoveryModuleChooserConfiguration
                .orElseGet(Throwing.supplier(() -> LinagoraServicesDiscoveryModuleChooserConfiguration.parse(propertiesProvider)));

            OpenPaasModuleChooserConfiguration openPaasModuleChooserConfiguration = this.openPaasModuleChooserConfiguration
                .orElseGet(Throwing.supplier(() -> OpenPaasModuleChooserConfiguration.parse(propertiesProvider)));

            boolean jmapEnabled = this.jmapEnabled.orElseGet(() -> {
                try {
                    return JMAPModule.parseConfiguration(propertiesProvider).isEnabled();
                } catch (FileNotFoundException e) {
                    return false;
                } catch (ConfigurationException e) {
                    throw new RuntimeException(e);
                }
            });

            TWPSettingsModuleChooserConfiguration twpSettingsModuleChooserConfiguration = this.twpSettingsModuleChooserConfiguration
                .orElseGet(Throwing.supplier(() -> TWPSettingsModuleChooserConfiguration.parse(propertiesProvider)));

            boolean quotaCompatibilityMode = this.quotaCompatibilityMode.orElseGet(() -> {
                try {
                    return propertiesProvider.getConfiguration("cassandra").getBoolean("quota.compatibility.mode", false);
                } catch (FileNotFoundException e) {
                    return false;
                } catch (ConfigurationException e) {
                    throw new RuntimeException(e);
                }
            });

            EventBusKeysChoice eventBusKeysChoice = this.eventBusKeysChoice.orElseGet(Throwing.supplier(
                () -> EventBusKeysChoice.parse(propertiesProvider)));

            boolean dropListsEnabled = this.dropListsEnabled.orElseGet(() -> {
                try {
                    return propertiesProvider.getConfiguration("droplists").getBoolean("enabled", false);
                } catch (FileNotFoundException e) {
                    return false;
                } catch (ConfigurationException e) {
                    throw new RuntimeException(e);
                }
            });

            VaultConfiguration vaultConfiguration = this.vaultConfiguration.orElseGet(() -> {
                try {
                    return VaultConfiguration.from(propertiesProvider.getConfiguration("deletedMessageVault"));
                } catch (FileNotFoundException e) {
                    return VaultConfiguration.ENABLED_DEFAULT;
                } catch (ConfigurationException e) {
                    throw new RuntimeException(e);
                }
            });

            ExtensionConfiguration extentionConfiguration = this.extentionConfiguration.orElseGet(Throwing.supplier(
                () -> {
                    try {
                        return ExtensionConfiguration.from(new PropertiesProvider(fileSystem, configurationPath).getConfiguration("extensions"));
                    } catch (FileNotFoundException e) {
                        return ExtensionConfiguration.DEFAULT;
                    }
                }));

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

            return new DistributedJamesConfiguration(
                configurationPath,
                directories,
                mailboxConfiguration,
                blobStoreConfiguration,
                searchConfiguration,
                usersRepositoryChoice,
                mailQueueViewChoice,
                firebaseModuleChooserConfiguration,
                servicesDiscoveryModuleChooserConfiguration,
                openPaasModuleChooserConfiguration,
                twpSettingsModuleChooserConfiguration,
                jmapEnabled,
                propertiesProvider,
                configurationProvider,
                quotaCompatibilityMode,
                eventBusKeysChoice,
                dropListsEnabled,
                vaultConfiguration,
                extentionConfiguration,
                oidcEnabled,
                oidcTokenCacheChoice);
        }
    }

    public static DistributedJamesConfiguration.Builder builder() {
        return new Builder();
    }

}
