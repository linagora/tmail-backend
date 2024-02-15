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
 * This class is copied & adapted from {@link org.apache.james.modules.blobstore.BlobStoreModulesChooser}
 */

package com.linagora.tmail.blob.blobid.list;

import java.util.List;
import java.util.Optional;

import org.apache.james.blob.aes.AESBlobStoreDAO;
import org.apache.james.blob.aes.CryptoConfig;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.cassandra.cache.CachedBlobStore;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreDAO;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.modules.blobstore.BlobDeduplicationGCModule;
import org.apache.james.modules.blobstore.validation.EventsourcingStorageStrategy;
import org.apache.james.modules.blobstore.validation.StorageStrategyModule;
import org.apache.james.modules.mailbox.BlobStoreAPIModule;
import org.apache.james.modules.objectstorage.S3BlobStoreModule;
import org.apache.james.modules.objectstorage.S3BucketModule;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.server.blob.deduplication.PassThroughBlobStore;
import org.apache.james.server.blob.deduplication.StorageStrategy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public class BlobStoreModulesChooser {
    private static final String WRAPPED = "wrapped";

    static class EncryptionModule extends AbstractModule {
        private final CryptoConfig cryptoConfig;

        EncryptionModule(CryptoConfig cryptoConfig) {
            this.cryptoConfig = cryptoConfig;
        }

        @Provides
        @Singleton
        @Named(WRAPPED)
        BlobStoreDAO blobStoreDAO(S3BlobStoreDAO unencrypted) {
            return new AESBlobStoreDAO(unencrypted, cryptoConfig);
        }

        @Provides
        @Named(WRAPPED)
        CryptoConfig cryptoConfig() {
            return cryptoConfig;
        }
    }

    static class NoEncryptionModule extends AbstractModule {
        @Provides
        @Singleton
        BlobStoreDAO blobStoreDAO(S3BlobStoreDAO unencrypted) {
            return unencrypted;
        }
    }

    public static Module chooseEncryptionModule(Optional<CryptoConfig> cryptoConfig) {
        Optional<Module> encryptionModule = cryptoConfig.map(EncryptionModule::new);
        return encryptionModule.orElse(new NoEncryptionModule());
    }

    static class SingleSaveDeclarationModule extends AbstractModule {
        @Override
        protected void configure() {
            install(new S3BucketModule());
            install(new S3BlobStoreModule());
            install(new SingleSaveBlobStoreModule());
        }

        @Provides
        @Singleton
        BlobStoreDAO blobStoreDAO(@Named(WRAPPED) BlobStoreDAO s3OrAes,
                                  BlobIdList blobIdList,
                                  BucketName defaultBucketName) {
            return new SingleSaveBlobStoreDAO(s3OrAes, blobIdList, defaultBucketName);
        }
    }

    static class MultiSaveDeclarationModule extends AbstractModule {
        @Override
        protected void configure() {
            install(new S3BucketModule());
            install(new S3BlobStoreModule());
            bind(BlobStoreDAO.class).annotatedWith(Names.named(WRAPPED)).to(S3BlobStoreDAO.class);
        }
    }

    public static Module chooseObjectStorageModule(boolean singleSaveEnabled) {
        if (singleSaveEnabled) {
            return new SingleSaveDeclarationModule();
        }
        return new MultiSaveDeclarationModule();
    }

    private static ImmutableList<Module> chooseStoragePolicyModule(StorageStrategy storageStrategy) {
        return switch (storageStrategy) {
            case DEDUPLICATION ->
                ImmutableList.of(new BlobDeduplicationGCModule(),
                    binder -> binder.bind(BlobStore.class)
                        .annotatedWith(Names.named(CachedBlobStore.BACKEND))
                        .to(DeDuplicationBlobStore.class));
            case PASSTHROUGH -> ImmutableList.of(
                binder -> binder.bind(BlobStore.class)
                    .annotatedWith(Names.named(CachedBlobStore.BACKEND))
                    .to(PassThroughBlobStore.class),
                new BlobStoreAPIModule());
        };
    }

    static class StoragePolicyConfigurationSanityEnforcementModule extends AbstractModule {
        private final BlobStoreConfiguration choosingConfiguration;

        StoragePolicyConfigurationSanityEnforcementModule(BlobStoreConfiguration choosingConfiguration) {
            this.choosingConfiguration = choosingConfiguration;
        }

        @Override
        protected void configure() {
            Multibinder<EventDTOModule<? extends Event, ? extends EventDTO>> eventDTOModuleBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<EventDTOModule<? extends Event, ? extends EventDTO>>() {
            });
            eventDTOModuleBinder.addBinding().toInstance(StorageStrategyModule.STORAGE_STRATEGY);

            bind(BlobStoreConfiguration.class).toInstance(choosingConfiguration);
            bind(EventsourcingStorageStrategy.class).in(Scopes.SINGLETON);

            Multibinder.newSetBinder(binder(), StartUpCheck.class)
                .addBinding()
                .to(BlobStoreConfigurationValidationStartUpCheck.class);
        }
    }

    @VisibleForTesting
    public static List<Module> chooseModules(BlobStoreConfiguration choosingConfiguration) {
        return ImmutableList.<Module>builder()
            .add(chooseEncryptionModule(choosingConfiguration.cryptoConfig()))
            .add(chooseObjectStorageModule(choosingConfiguration.singleSaveEnabled()))
            .addAll(chooseStoragePolicyModule(choosingConfiguration.storageStrategy()))
            .add(new StoragePolicyConfigurationSanityEnforcementModule(choosingConfiguration))
            .build();
    }
}
