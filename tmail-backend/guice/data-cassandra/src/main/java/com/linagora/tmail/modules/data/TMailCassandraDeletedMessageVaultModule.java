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

package com.linagora.tmail.modules.data;

import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.events.EventListener;
import org.apache.james.mailbox.cassandra.DeleteMessageListener;
import org.apache.james.vault.DeletedMessageVaultDeletionListener;
import org.apache.james.vault.metadata.DeletedMessageMetadataDataDefinition;
import org.apache.james.vault.metadata.DeletedMessageMetadataVault;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.linagora.tmail.vault.TMailDeletedMessageVaultModule;
import com.linagora.tmail.vault.cassandra.TmailCassandraDeletedMessageMetadataVault;
import com.linagora.tmail.vault.cassandra.TmailMetadataDAO;
import com.linagora.tmail.vault.cassandra.TmailStorageInformationDAO;
import com.linagora.tmail.vault.cassandra.TmailUserPerBucketDAO;
import com.linagora.tmail.vault.dto.TmailDeletedMessageWithStorageInformationConverter;

public class TMailCassandraDeletedMessageVaultModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new TMailDeletedMessageVaultModule());

        Multibinder<CassandraDataDefinition> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraDataDefinition.class);
        cassandraDataDefinitions
            .addBinding()
            .toInstance(DeletedMessageMetadataDataDefinition.MODULE);

        bind(TmailMetadataDAO.class).in(Scopes.SINGLETON);
        bind(TmailStorageInformationDAO.class).in(Scopes.SINGLETON);
        bind(TmailUserPerBucketDAO.class).in(Scopes.SINGLETON);
        bind(TmailDeletedMessageWithStorageInformationConverter.class).in(Scopes.SINGLETON);

        bind(TmailCassandraDeletedMessageMetadataVault.class).in(Scopes.SINGLETON);
        bind(DeletedMessageMetadataVault.class)
            .to(TmailCassandraDeletedMessageMetadataVault.class);

        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class, Names.named(DeleteMessageListener.CONTENT_DELETION))
            .addBinding()
            .to(DeletedMessageVaultDeletionListener.class);
    }
}
