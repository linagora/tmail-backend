/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.app;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.linagora.tmail.encrypted.ClearEmailContentFactory;
import com.linagora.tmail.encrypted.EncryptedMailboxManager;
import com.linagora.tmail.encrypted.KeystoreManager;
import com.linagora.tmail.encrypted.cassandra.CassandraEncryptedEmailContentStore;
import com.linagora.tmail.encrypted.cassandra.EncryptedEmailContentStoreCassandraModule;
import com.linagora.tmail.encrypted.cassandra.KeystoreCassandraModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailDetailedViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailFastViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreSetMethodModule;

public class DistributedEncryptedMailboxModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new KeystoreCassandraModule());
        install(new KeystoreSetMethodModule());
        install(new KeystoreGetMethodModule());
        install(new EncryptedEmailContentStoreCassandraModule());
        install(new EncryptedEmailDetailedViewGetMethodModule());
        install(new EncryptedEmailFastViewGetMethodModule());
    }

    @Provides
    @Singleton
    MailboxManager provide(CassandraMailboxManager mailboxManager, KeystoreManager keystoreManager,
                           ClearEmailContentFactory clearEmailContentFactory,
                           CassandraEncryptedEmailContentStore contentStore) {
        return new EncryptedMailboxManager(mailboxManager, keystoreManager, clearEmailContentFactory, contentStore);
    }
}
