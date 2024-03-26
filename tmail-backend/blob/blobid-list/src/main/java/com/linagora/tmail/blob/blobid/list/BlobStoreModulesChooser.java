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

import static com.linagora.tmail.blob.blobid.list.BlobStoreModulesChooser.SingleSaveDeclarationModule.BackedStorage.CASSANDRA;

import java.util.List;
import java.util.Optional;

import org.apache.james.blob.aes.AESBlobStoreDAO;
import org.apache.james.blob.aes.CryptoConfig;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectStorageHealthCheck;
import org.apache.james.blob.cassandra.cache.CachedBlobStore;
import org.apache.james.blob.file.FileBlobStoreDAO;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreDAO;
import org.apache.james.blob.postgres.PostgresBlobStoreDAO;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.dto.EventDTOModule;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.modules.blobstore.BlobDeduplicationGCModule;
import org.apache.james.modules.blobstore.validation.EventsourcingStorageStrategy;
import org.apache.james.modules.blobstore.validation.StorageStrategyModule;
import org.apache.james.modules.mailbox.BlobStoreAPIModule;
import org.apache.james.modules.mailbox.DefaultBucketModule;
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
import com.linagora.tmail.blob.blobid.list.postgres.PostgresSingleSaveBlobStoreModule;

import modules.BlobPostgresModule;

public class BlobStoreModulesChooser {
    private static final String TOP_NAMED = "top";
    private static final String PRE_LAST_NAMED = "pre_last";
    private static final String LAST_NAMED = "last";

    static class ObjectStorageBlobStoreDAODeclarationModule extends AbstractModule {
        @Override
        protected void configure() {
            install(new S3BlobStoreModule());
            install(new S3BucketModule());

            bind(BlobStoreDAO.class)
                .annotatedWith(Names.named(TOP_NAMED))
                .to(S3BlobStoreDAO.class)
                .in(Scopes.SINGLETON);

            Multibinder.newSetBinder(binder(), HealthCheck.class).addBinding().to(ObjectStorageHealthCheck.class);
        }
    }

    static class FileBlobStoreDAODeclarationModule extends AbstractModule {
        @Override
        protected void configure() {
            install(new DefaultBucketModule());
            bind(BucketName.class)
                .toInstance(BucketName.DEFAULT);

            bind(BlobStoreDAO.class).annotatedWith(Names.named(TOP_NAMED))
                .to(FileBlobStoreDAO.class)
                .in(Scopes.SINGLETON);
        }
    }

    static class PostgresBlobStoreDAODeclarationModule extends AbstractModule {
        @Override
        protected void configure() {
            install(new BlobPostgresModule());

            install(new DefaultBucketModule());
            bind(BucketName.class)
                .toInstance(BucketName.DEFAULT);

            bind(BlobStoreDAO.class).annotatedWith(Names.named(TOP_NAMED))
                .to(PostgresBlobStoreDAO.class)
                .in(Scopes.SINGLETON);
        }
    }

    static class EncryptionModule extends AbstractModule {
        private final CryptoConfig cryptoConfig;

        EncryptionModule(CryptoConfig cryptoConfig) {
            this.cryptoConfig = cryptoConfig;
        }

        @Provides
        CryptoConfig cryptoConfig() {
            return cryptoConfig;
        }

        @Provides
        @Singleton
        @Named(PRE_LAST_NAMED)
        BlobStoreDAO providePrelastBlobStoreDAO(@Named(TOP_NAMED) BlobStoreDAO blobStoreDAO) {
            return new AESBlobStoreDAO(blobStoreDAO, cryptoConfig);
        }

    }

    static class NoEncryptionModule extends AbstractModule {
        @Provides
        @Singleton
        @Named(PRE_LAST_NAMED)
        BlobStoreDAO providePrelastBlobStoreDAO(@Named(TOP_NAMED) BlobStoreDAO blobStoreDAO) {
            return blobStoreDAO;
        }
    }

    public static Module chooseEncryptionModule(Optional<CryptoConfig> cryptoConfig) {
        if (cryptoConfig.isPresent()) {
            return new EncryptionModule(cryptoConfig.get());
        }
        return new NoEncryptionModule();
    }

    public static class SingleSaveDeclarationModule extends AbstractModule {
        public enum BackedStorage {
            CASSANDRA,
            POSTGRES
        }

        private final BackedStorage backedStorage;

        public SingleSaveDeclarationModule(BackedStorage backedStorage) {
            this.backedStorage = backedStorage;
        }

        @Override
        protected void configure() {
            if (backedStorage == CASSANDRA) {
                install(new CassandraSingleSaveBlobStoreModule());
            } else {
                install(new PostgresSingleSaveBlobStoreModule());
            }
        }

        @Provides
        @Singleton
        SingleSaveBlobStoreDAO singleSaveBlobStoreDAO(@Named(PRE_LAST_NAMED) BlobStoreDAO blobStoreDAO,
                                                      BlobIdList blobIdList,
                                                      BucketName defaultBucketName) {
            return new SingleSaveBlobStoreDAO(blobStoreDAO, blobIdList, defaultBucketName);
        }

        @Provides
        @Singleton
        @Named(LAST_NAMED)
        public BlobStoreDAO provideLast(SingleSaveBlobStoreDAO blobStoreDAO) {
            return blobStoreDAO;
        }
    }

    static class MultiSaveDeclarationModule extends AbstractModule {

        @Provides
        @Singleton
        @Named(LAST_NAMED)
        public BlobStoreDAO provideLastBlobStoreDAO(@Named(PRE_LAST_NAMED) BlobStoreDAO blobStoreDAO) {
            return blobStoreDAO;
        }
    }

    static class BaseDeclarationModule extends AbstractModule {
        @Provides
        @Singleton
        BlobStoreDAO provdePrimaryBlobStoreDAO(@Named(LAST_NAMED) BlobStoreDAO blobStoreDAO) {
            return blobStoreDAO;
        }
    }

    public static ImmutableList<Module> chooseObjectStorageModule(boolean singleSaveEnabled, SingleSaveDeclarationModule.BackedStorage backedSingleSaveStorage) {
        ImmutableList.Builder<Module> modulesBuilder = ImmutableList.<Module>builder()
            .add(new BaseDeclarationModule());
        if (singleSaveEnabled) {
            return modulesBuilder.add(new SingleSaveDeclarationModule(backedSingleSaveStorage)).build();
        }
        return modulesBuilder.add(new MultiSaveDeclarationModule()).build();
    }

    private static ImmutableList<Module> chooseStoragePolicyModule(StorageStrategy storageStrategy) {
        return switch (storageStrategy) {
            case DEDUPLICATION -> ImmutableList.of(new BlobDeduplicationGCModule(),
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

    public static Module chooseBlobStoreDAOModule(BlobStoreConfiguration.BlobStoreImplName implementation) {
        switch (implementation) {
            case S3:
                return new ObjectStorageBlobStoreDAODeclarationModule();
            case FILE:
                return new FileBlobStoreDAODeclarationModule();
            case POSTGRES:
                return new PostgresBlobStoreDAODeclarationModule();
            default:
                throw new RuntimeException("Unsupported blobStore implementation " + implementation);
        }
    }

    public static List<Module> chooseModules(BlobStoreConfiguration choosingConfiguration, SingleSaveDeclarationModule.BackedStorage backedSingleSaveStorage) {
        return ImmutableList.<Module>builder()
            .add(chooseBlobStoreDAOModule(choosingConfiguration.implementation()))
            .add(chooseEncryptionModule(choosingConfiguration.cryptoConfig()))
            .addAll(chooseObjectStorageModule(choosingConfiguration.singleSaveEnabled(), backedSingleSaveStorage))
            .addAll(chooseStoragePolicyModule(choosingConfiguration.storageStrategy()))
            .add(new StoragePolicyConfigurationSanityEnforcementModule(choosingConfiguration))
            .build();
    }

    @VisibleForTesting
    public static List<Module> chooseModules(BlobStoreConfiguration choosingConfiguration) {
        return chooseModules(choosingConfiguration, CASSANDRA);
    }
}
