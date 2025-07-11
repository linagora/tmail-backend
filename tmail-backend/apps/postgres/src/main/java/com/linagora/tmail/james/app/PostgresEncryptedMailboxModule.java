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
import org.apache.james.mailbox.postgres.PostgresMailboxManager;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import com.linagora.tmail.encrypted.ClearEmailContentFactory;
import com.linagora.tmail.encrypted.EncryptedMailboxManager;
import com.linagora.tmail.encrypted.KeystoreManager;
import com.linagora.tmail.encrypted.postgres.PostgresEncryptedEmailContentStore;
import com.linagora.tmail.encrypted.postgres.PostgresEncryptedEmailContentStoreModule;
import com.linagora.tmail.encrypted.postgres.PostgresKeystoreModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailDetailedViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailFastViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreSetMethodModule;

public class PostgresEncryptedMailboxModule extends AbstractModule {

    @Override
    protected void configure() {
        install(Modules.combine(new PostgresKeystoreModule(), new KeystoreSetMethodModule(), new KeystoreGetMethodModule(),
            new EncryptedEmailDetailedViewGetMethodModule(), new EncryptedEmailFastViewGetMethodModule(), new PostgresEncryptedEmailContentStoreModule()));
    }

    @Provides
    @Singleton
    MailboxManager provide(PostgresMailboxManager mailboxManager, KeystoreManager keystoreManager,
                           ClearEmailContentFactory clearEmailContentFactory,
                           PostgresEncryptedEmailContentStore contentStore) {
        return new EncryptedMailboxManager(mailboxManager, keystoreManager, clearEmailContentFactory, contentStore);
    }
}
