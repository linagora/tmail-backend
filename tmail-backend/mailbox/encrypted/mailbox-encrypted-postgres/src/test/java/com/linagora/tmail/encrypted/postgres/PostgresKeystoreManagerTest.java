package com.linagora.tmail.encrypted.postgres;

import org.apache.james.backends.postgres.PostgresExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.encrypted.KeystoreManager;
import com.linagora.tmail.encrypted.KeystoreManagerContract;
import com.linagora.tmail.encrypted.postgres.table.PostgresKeystoreModule;

public class PostgresKeystoreManagerTest implements KeystoreManagerContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(PostgresKeystoreModule.MODULE);

    @Override
    public KeystoreManager keyStoreManager() {
        return new PostgresKeystoreManager(new PostgresKeystoreDAO.Factory(postgresExtension.getExecutorFactory()));
    }
}
