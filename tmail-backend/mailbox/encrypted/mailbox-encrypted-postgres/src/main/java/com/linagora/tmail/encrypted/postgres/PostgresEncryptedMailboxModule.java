package com.linagora.tmail.encrypted.postgres;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.postgres.PostgresMailboxManager;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.linagora.tmail.encrypted.ClearEmailContentFactory;
import com.linagora.tmail.encrypted.EncryptedMailboxManager;
import com.linagora.tmail.encrypted.KeystoreManager;

public class PostgresEncryptedMailboxModule extends AbstractModule {

    @Provides
    @Singleton
    MailboxManager provide(PostgresMailboxManager mailboxManager, KeystoreManager keystoreManager,
                           ClearEmailContentFactory clearEmailContentFactory,
                           PostgresEncryptedEmailContentStore contentStore) {
        return new EncryptedMailboxManager(mailboxManager, keystoreManager, clearEmailContentFactory, contentStore);
    }
}
