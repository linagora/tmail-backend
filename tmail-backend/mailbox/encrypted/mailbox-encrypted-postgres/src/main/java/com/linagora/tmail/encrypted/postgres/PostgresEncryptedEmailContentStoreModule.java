package com.linagora.tmail.encrypted.postgres;

import org.apache.james.backends.postgres.PostgresModule;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.encrypted.EncryptedEmailContentStore;

public class PostgresEncryptedEmailContentStoreModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(PostgresEncryptedEmailStoreDAO.class).in(Scopes.SINGLETON);
        bind(PostgresEncryptedEmailContentStore.class).in(Scopes.SINGLETON);
        bind(EncryptedEmailContentStore.class).to(PostgresEncryptedEmailContentStore.class);

        Multibinder<PostgresModule> postgresDataDefinitions = Multibinder.newSetBinder(binder(), PostgresModule.class);
        postgresDataDefinitions.addBinding().toInstance(com.linagora.tmail.encrypted.postgres.table.PostgresEncryptedEmailStoreModule.MODULE);
    }
}
