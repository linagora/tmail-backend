import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.jmap.JMAPTestingConstants;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.CassandraTestSystemFixture;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.openpaas.encrypted.EncryptedMessageManager;
import com.linagora.openpaas.encrypted.EncryptedMessageManagerContract;
import com.linagora.openpaas.encrypted.KeystoreManager;
import com.linagora.openpaas.encrypted.cassandra.CassandraKeystoreDAO;
import com.linagora.openpaas.encrypted.cassandra.CassandraKeystoreManager;
import com.linagora.openpaas.encrypted.cassandra.table.CassandraKeystoreModule;

import java.security.Provider;
import java.security.Security;

public class EncryptedCassandraMessageManagerTest implements EncryptedMessageManagerContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(CassandraKeystoreModule.MODULE(),
            MailboxAggregateModule.MODULE_WITH_QUOTA,
            CassandraSchemaVersionModule.MODULE));

    private MailboxManager mailboxManager;
    private MessageManager messageManager;
    private KeystoreManager keystoreManager;
    private MailboxPath path;
    private MailboxSession session;
    private EncryptedMessageManager testee;
    private CassandraKeystoreDAO cassandraKeystoreDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        String bouncyCastleProviderClassName = "org.bouncycastle.jce.provider.BouncyCastleProvider";
        Security.addProvider((Provider)Class.forName(bouncyCastleProviderClassName).getDeclaredConstructor().newInstance());
    }

    @BeforeEach
    void setUp(CassandraCluster cassandra) throws Exception {
        CassandraMailboxSessionMapperFactory mapperFactory = CassandraTestSystemFixture.createMapperFactory(cassandra);
        mailboxManager = CassandraTestSystemFixture.createMailboxManager(mapperFactory);
        session = mailboxManager.createSystemSession(JMAPTestingConstants.BOB);
        path = MailboxPath.inbox(session);
        MailboxId mailboxId = mailboxManager.createMailbox(path, session).get();
        messageManager = mailboxManager.getMailbox(mailboxId, session);
        cassandraKeystoreDAO = new CassandraKeystoreDAO(cassandra.getConf(), cassandra.getTypesProvider());
        keystoreManager = new CassandraKeystoreManager(cassandraKeystoreDAO);
        testee = new EncryptedMessageManager(messageManager, keystoreManager);
    }

    @Override
    public KeystoreManager keystoreManager() {
        return keystoreManager;
    }

    @Override
    public MailboxManager mailboxManager() {
        return mailboxManager;
    }

    @Override
    public MailboxPath path() {
        return path;
    }

    @Override
    public MailboxSession session() {
        return session;
    }

    @Override
    public MessageManager messageManager() {
        return messageManager;
    }

    @Override
    public EncryptedMessageManager testee() {
        return testee;
    }
}
