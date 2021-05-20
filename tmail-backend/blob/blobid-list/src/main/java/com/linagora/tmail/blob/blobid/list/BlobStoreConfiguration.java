/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

/**
 * This class is copied & adapted from {@link org.apache.james.modules.blobstore.BlobStoreConfiguration}
 */

package com.linagora.tmail.blob.blobid.list;

import java.io.FileNotFoundException;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.blob.aes.CryptoConfig;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.server.blob.deduplication.StorageStrategy;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

import io.vavr.control.Try;

public class BlobStoreConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlobStoreConfiguration.class);

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

    public static RequireCache builder() {
        return enableCache -> storageStrategy -> cryptoConfig -> enableSingleSave ->
            new BlobStoreConfiguration(enableCache, storageStrategy, cryptoConfig, enableSingleSave);
    }

    static final String CACHE_ENABLE_PROPERTY = "cache.enable";
    static final String ENCRYPTION_ENABLE_PROPERTY = "encryption.aes.enable";
    static final String ENCRYPTION_PASSWORD_PROPERTY = "encryption.aes.password";
    static final String ENCRYPTION_SALT_PROPERTY = "encryption.aes.salt";
    static final boolean CACHE_ENABLED = true;
    static final String DEDUPLICATION_ENABLE_PROPERTY = "deduplication.enable";
    static final String SINGLE_SAVE_ENABLE_PROPERTY = "single.save.enable";

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
                .disableCache()
                .passthrough()
                .noCryptoConfig()
                .disableSingleSave();
        }
    }

    static BlobStoreConfiguration from(Configuration configuration) {
        boolean cacheEnabled = configuration.getBoolean(CACHE_ENABLE_PROPERTY, false);
        boolean deduplicationEnabled = Try.ofCallable(() -> configuration.getBoolean(DEDUPLICATION_ENABLE_PROPERTY))
                .getOrElseThrow(() -> new IllegalStateException("deduplication.enable property is missing please use one of the supported values in: true, false\n" +
                        "If you choose to enable deduplication, the mails with the same content will be stored only once.\n" +
                        "Warning: Once this feature is enabled, there is no turning back as turning it off will lead to the deletion of all\n" +
                        "the mails sharing the same content once one is deleted.\n" +
                        "Upgrade note: If you are upgrading from James 3.5 or older, the deduplication was enabled."));
        Optional<CryptoConfig> cryptoConfig = parseCryptoConfig(configuration);

        boolean singleSaveEnabled = configuration.getBoolean(SINGLE_SAVE_ENABLE_PROPERTY, false);

        if (deduplicationEnabled) {
            return builder()
                .enableCache(cacheEnabled)
                .deduplication()
                .cryptoConfig(cryptoConfig)
                .enableSingleSave(singleSaveEnabled);
        } else {
            return builder()
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

    private final boolean cacheEnabled;
    private final StorageStrategy storageStrategy;
    private final Optional<CryptoConfig> cryptoConfig;
    private final boolean singleSaveEnabled;

    BlobStoreConfiguration(boolean cacheEnabled, StorageStrategy storageStrategy, Optional<CryptoConfig> cryptoConfig, boolean singleSaveEnabled) {
        this.cacheEnabled = cacheEnabled;
        this.storageStrategy = storageStrategy;
        this.cryptoConfig = cryptoConfig;
        this.singleSaveEnabled = singleSaveEnabled;
    }

    public boolean cacheEnabled() {
        return cacheEnabled;
    }

    public StorageStrategy storageStrategy() {
        return storageStrategy;
    }

    public Optional<CryptoConfig> getCryptoConfig() {
        return cryptoConfig;
    }

    public boolean isSingleSaveEnabled() {
        return singleSaveEnabled;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof BlobStoreConfiguration) {
            BlobStoreConfiguration that = (BlobStoreConfiguration) o;

            return Objects.equals(this.cacheEnabled, that.cacheEnabled)
                && Objects.equals(this.storageStrategy, that.storageStrategy)
                && Objects.equals(this.cryptoConfig, that.cryptoConfig)
                && Objects.equals(this.singleSaveEnabled, that.singleSaveEnabled);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(cacheEnabled, storageStrategy, cryptoConfig, singleSaveEnabled);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("cacheEnabled", cacheEnabled)
            .add("storageStrategy", storageStrategy.name())
            .add("cryptoConfig", cryptoConfig)
            .add("singleSaveEnabled", singleSaveEnabled)
            .toString();
    }
}
