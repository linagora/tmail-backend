package com.linagora.tmail.james.app;

import static org.apache.james.JamesServerMain.LOGGER;
import static org.apache.james.MemoryJamesServerMain.JMAP;
import static org.apache.james.MemoryJamesServerMain.WEBADMIN;

import java.util.List;

import org.apache.james.FakeSearchMailboxModule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerMain;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.modules.BlobExportMechanismModule;
import org.apache.james.modules.BlobMemoryModule;
import org.apache.james.modules.MailboxModule;
import org.apache.james.modules.MailetProcessingModule;
import org.apache.james.modules.data.MemoryDataModule;
import org.apache.james.modules.eventstore.MemoryEventStoreModule;
import org.apache.james.modules.mailbox.MemoryMailboxModule;
import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.queue.memory.MemoryMailQueueModule;
import org.apache.james.modules.server.DKIMMailetModule;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.modules.server.TaskManagerModule;
import org.apache.james.modules.spamassassin.SpamAssassinListenerModule;
import org.apache.james.modules.vault.DeletedMessageVaultModule;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import com.linagora.tmail.encrypted.ClearEmailContentFactory;
import com.linagora.tmail.encrypted.EncryptedMailboxManager;
import com.linagora.tmail.encrypted.InMemoryEncryptedEmailContentStore;
import com.linagora.tmail.encrypted.InMemoryEncryptedEmailContentStoreModule;
import com.linagora.tmail.encrypted.KeystoreManager;
import com.linagora.tmail.encrypted.KeystoreMemoryModule;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.james.jmap.method.CustomMethodModule;
import com.linagora.tmail.james.jmap.method.EmailSendMethodModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailDetailedViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailFastViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.FilterGetMethodModule;
import com.linagora.tmail.james.jmap.method.FilterSetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreSetMethodModule;
import com.linagora.tmail.james.jmap.ticket.TicketRoutesModule;

public class MemoryServer {
    public static final Module IN_MEMORY_SERVER_MODULE = Modules.combine(
        new MailetProcessingModule(),
        new BlobMemoryModule(),
        new DeletedMessageVaultModule(),
        new BlobExportMechanismModule(),
        new MailboxModule(),
        new MemoryDataModule(),
        new MemoryEventStoreModule(),
        new MemoryMailboxModule(),
        new MemoryMailQueueModule(),
        new TaskManagerModule());

    public static final Module PROTOCOLS = Modules.combine(
        new IMAPServerModule(),
        new ProtocolHandlerModule(),
        new SMTPServerModule());

    public static final Module JMAP_LINAGORA = Modules.combine(
        JMAP,
        new CustomMethodModule(),
        new EncryptedEmailDetailedViewGetMethodModule(),
        new EncryptedEmailFastViewGetMethodModule(),
        new EmailSendMethodModule(),
        new FilterGetMethodModule(),
        new FilterSetMethodModule(),
        new InMemoryEncryptedEmailContentStoreModule(),
        new KeystoreMemoryModule(),
        new KeystoreSetMethodModule(),
        new KeystoreGetMethodModule(),
        new TicketRoutesModule());

    public static final Module MODULES = Modules.combine(
        IN_MEMORY_SERVER_MODULE,
        PROTOCOLS,
        JMAP_LINAGORA,
        WEBADMIN,
        new DKIMMailetModule(),
        new SpamAssassinListenerModule());

    public static void main(String[] args) throws Exception {
        MemoryConfiguration configuration = MemoryConfiguration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();

        LOGGER.info("Loading configuration {}", configuration.toString());
        GuiceJamesServer server = createServer(configuration)
            .combineWith(new FakeSearchMailboxModule(), new JMXServerModule());

        JamesServerMain.main(server);
    }

    public static GuiceJamesServer createServer(MemoryConfiguration configuration) {
        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(MODULES)
            .overrideWith(chooseMailbox(configuration.mailboxConfiguration()));
    }

    private static class EncryptedMailboxModule extends AbstractModule {
        @Provides
        @Singleton
        MailboxManager provide(InMemoryMailboxManager mailboxManager, KeystoreManager keystoreManager,
                               ClearEmailContentFactory clearEmailContentFactory,
                               InMemoryEncryptedEmailContentStore contentStore) {
            return new EncryptedMailboxManager(mailboxManager, keystoreManager, clearEmailContentFactory, contentStore);
        }
    }

    private static List<Module> chooseMailbox(MailboxConfiguration mailboxConfiguration) {
        if (mailboxConfiguration.isEncryptionEnabled()) {
            return ImmutableList.of(new EncryptedMailboxModule());
        }
        return ImmutableList.of();
    }
}
