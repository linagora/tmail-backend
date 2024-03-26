package com.linagora.tmail.blob.guice;

import java.io.FileNotFoundException;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.blob.aes.CryptoConfig;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.server.blob.deduplication.StorageStrategy;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Try;

public record BlobStoreConfiguration(BlobStoreImplName implementation,
                                     Optional<SecondaryS3BlobStoreConfiguration> maybeSecondaryS3BlobStoreConfiguration,
                                     boolean cacheEnabled,
                                     StorageStrategy storageStrategy,
                                     Optional<CryptoConfig> cryptoConfig,
                                     boolean singleSaveEnabled) {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlobStoreConfiguration.class);

    @FunctionalInterface
    public interface RequireImplementation {
        RequireSecondaryS3BlobStoreConfig implementation(BlobStoreImplName implementation);

        default RequireCache file() {
            return implementation(BlobStoreImplName.FILE)
                .noSecondaryS3BlobStore();
        }

        default RequireSecondaryS3BlobStoreConfig s3() {
            return implementation(BlobStoreImplName.S3);
        }

        default RequireCache postgres() {
            return implementation(BlobStoreImplName.POSTGRES)
                .noSecondaryS3BlobStore();
        }
    }

    @FunctionalInterface
    public interface RequireSecondaryS3BlobStoreConfig {
        RequireCache secondaryS3BlobStore(Optional<SecondaryS3BlobStoreConfiguration> maybeSecondaryS3BlobStoreConfiguration);

        default RequireCache noSecondaryS3BlobStore() {
            return secondaryS3BlobStore(Optional.empty());
        }

        default RequireCache secondaryS3BlobStore(SecondaryS3BlobStoreConfiguration secondaryS3BlobStoreConfiguration) {
            return secondaryS3BlobStore(Optional.of(secondaryS3BlobStoreConfiguration));
        }
    }

    @FunctionalInterface
    public interface RequireCache {
        RequireStoringStrategy enableCache(boolean enable);

        default RequireStoringStrategy enableCache() {
            return enableCache(CACHE_ENABLED);
        }

        default RequireStoringStrategy disableCache() {
            return enableCache(!CACHE_ENABLED);
        }
    }

    @FunctionalInterface
    public interface RequireStoringStrategy {
        RequireCryptoConfig strategy(StorageStrategy storageStrategy);

        default RequireCryptoConfig passthrough() {
            return strategy(StorageStrategy.PASSTHROUGH);
        }

        default RequireCryptoConfig deduplication() {
            return strategy(StorageStrategy.DEDUPLICATION);
        }
    }

    @FunctionalInterface
    public interface RequireCryptoConfig {
        RequireSingleSave cryptoConfig(Optional<CryptoConfig> cryptoConfig);

        default RequireSingleSave noCryptoConfig() {
            return cryptoConfig(Optional.empty());
        }

        default RequireSingleSave cryptoConfig(CryptoConfig cryptoConfig) {
            return cryptoConfig(Optional.of(cryptoConfig));
        }
    }
    
    @FunctionalInterface
    public interface RequireSingleSave {
        BlobStoreConfiguration enableSingleSave(boolean enable);

        default BlobStoreConfiguration disableSingleSave() {
            return enableSingleSave(false);
        }

        default BlobStoreConfiguration enableSingleSave() {
            return enableSingleSave(true);
        }
    }

    public static RequireImplementation builder() {
        return implementation -> secondaryS3BlobStoreConfig -> enableCache -> storageStrategy -> cryptoConfig -> enableSingleSave ->
            new BlobStoreConfiguration(implementation, secondaryS3BlobStoreConfig, enableCache, storageStrategy, cryptoConfig, enableSingleSave);
    }

    public enum BlobStoreImplName {
        FILE("file"),
        S3("s3"),
        POSTGRES("postgres");

        static String supportedImplNames() {
            return Stream.of(BlobStoreImplName.values())
                .map(BlobStoreImplName::getName)
                .collect(Collectors.joining(", "));
        }

        static BlobStoreImplName from(String name) {
            return Stream.of(values())
                .filter(blobName -> blobName.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("%s is not a valid name of BlobStores, " +
                    "please use one of supported values in: %s", name, supportedImplNames())));
        }

        private final String name;

        BlobStoreImplName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    static final String BLOBSTORE_IMPLEMENTATION_PROPERTY = "implementation";
    static final String CACHE_ENABLE_PROPERTY = "cache.enable";
    static final String ENCRYPTION_ENABLE_PROPERTY = "encryption.aes.enable";
    static final String ENCRYPTION_PASSWORD_PROPERTY = "encryption.aes.password";
    static final String ENCRYPTION_SALT_PROPERTY = "encryption.aes.salt";
    static final boolean CACHE_ENABLED = true;
    static final String DEDUPLICATION_ENABLE_PROPERTY = "deduplication.enable";
    static final String SINGLE_SAVE_ENABLE_PROPERTY = "single.save.enable";
    private static final String OBJECT_STORAGE_S3_SECONDARY_ENABLED = "objectstorage.s3.secondary.enabled";

    public static BlobStoreConfiguration parse(org.apache.james.server.core.configuration.Configuration configuration) throws ConfigurationException {
        PropertiesProvider propertiesProvider = new PropertiesProvider(new FileSystemImpl(configuration.directories()),
            configuration.configurationPath());

        return parse(propertiesProvider);
    }

    public static BlobStoreConfiguration parse(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfigurations(ConfigurationComponent.NAMES);
            return BlobStoreConfiguration.from(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + ConfigurationComponent.NAME + " configuration file, using s3 blobstore as the default");
            return BlobStoreConfiguration.builder()
                .implementation(BlobStoreImplName.S3)
                .noSecondaryS3BlobStore()
                .disableCache()
                .passthrough()
                .noCryptoConfig()
                .disableSingleSave();
        }
    }

    static BlobStoreConfiguration from(Configuration configuration) throws ConfigurationException {
        BlobStoreImplName blobStoreImplName = Optional.ofNullable(configuration.getString(BLOBSTORE_IMPLEMENTATION_PROPERTY))
            .filter(StringUtils::isNotBlank)
            .map(StringUtils::trim)
            .map(BlobStoreImplName::from)
            .orElse(BlobStoreImplName.S3);

        boolean cacheEnabled = configuration.getBoolean(CACHE_ENABLE_PROPERTY, false);
        boolean deduplicationEnabled = Try.ofCallable(() -> configuration.getBoolean(DEDUPLICATION_ENABLE_PROPERTY))
                .getOrElseThrow(() -> new IllegalStateException("""
                    deduplication.enable property is missing please use one of the supported values in: true, false
                    If you choose to enable deduplication, the mails with the same content will be stored only once.
                    Warning: Once this feature is enabled, there is no turning back as turning it off will lead to the deletion of all
                    the mails sharing the same content once one is deleted.
                    Upgrade note: If you are upgrading from James 3.5 or older, the deduplication was enabled."""));
        Optional<CryptoConfig> cryptoConfig = parseCryptoConfig(configuration);

        boolean singleSaveEnabled = configuration.getBoolean(SINGLE_SAVE_ENABLE_PROPERTY, false);

        if (deduplicationEnabled) {
            return builder()
                .implementation(BlobStoreImplName.S3)
                .secondaryS3BlobStore(parseS3BlobStoreConfiguration(configuration))
                .enableCache(cacheEnabled)
                .deduplication()
                .cryptoConfig(cryptoConfig)
                .enableSingleSave(singleSaveEnabled);
        } else {
            return builder()
                .implementation(BlobStoreImplName.S3)
                .secondaryS3BlobStore(parseS3BlobStoreConfiguration(configuration))
                .enableCache(cacheEnabled)
                .passthrough()
                .cryptoConfig(cryptoConfig)
                .enableSingleSave(singleSaveEnabled);
        }
    }

    private static Optional<CryptoConfig> parseCryptoConfig(Configuration configuration) {
        final boolean enabled = configuration.getBoolean(ENCRYPTION_ENABLE_PROPERTY, false);
        if (enabled) {
            return Optional.of(CryptoConfig.builder()
                .password(Optional.ofNullable(configuration.getString(ENCRYPTION_PASSWORD_PROPERTY, null)).map(String::toCharArray).orElse(null))
                .salt(configuration.getString(ENCRYPTION_SALT_PROPERTY, null))
                .build());
        }
        return Optional.empty();
    }

    private static Optional<SecondaryS3BlobStoreConfiguration> parseS3BlobStoreConfiguration(Configuration configuration) throws ConfigurationException {
        if (configuration.getBoolean(OBJECT_STORAGE_S3_SECONDARY_ENABLED, false)) {
            return Optional.of(SecondaryS3BlobStoreConfigurationReader.from(configuration));
        } else {
            return Optional.empty();
        }
    }
}
