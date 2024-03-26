package com.linagora.tmail.encrypted.postgres;

import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.user.api.UsernameChangeTaskStep;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.encrypted.KeystoreManager;
import com.linagora.tmail.encrypted.PGPKeysUserDeletionTaskStep;
import com.linagora.tmail.encrypted.PGPKeysUsernameChangeTaskStep;

public class PostgresKeystoreModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(KeystoreManager.class).to(PostgresKeystoreManager.class);
        bind(PostgresKeystoreManager.class).in(Scopes.SINGLETON);

        Multibinder<PostgresModule> postgresDataDefinitions = Multibinder.newSetBinder(binder(), PostgresModule.class);
        postgresDataDefinitions.addBinding().toInstance(com.linagora.tmail.encrypted.postgres.table.PostgresKeystoreModule.MODULE);

        Multibinder.newSetBinder(binder(), UsernameChangeTaskStep.class).addBinding().to(PGPKeysUsernameChangeTaskStep.class);
        Multibinder.newSetBinder(binder(), DeleteUserDataTaskStep.class).addBinding().to(PGPKeysUserDeletionTaskStep.class);
    }
}
