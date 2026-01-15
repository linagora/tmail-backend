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

import static org.apache.james.mailbox.postgres.DeleteMessageListener.CONTENT_DELETION;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.events.EventListener;
import org.apache.james.vault.DeletedMessageVaultDeletionListener;
import org.apache.james.vault.metadata.DeletedMessageMetadataVault;
import org.apache.james.vault.metadata.PostgresDeletedMessageMetadataDataDefinition;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.linagora.tmail.vault.TMailDeletedMessageVaultModule;
import com.linagora.tmail.vault.dto.TmailDeletedMessageWithStorageInformationConverter;
import com.linagora.tmail.vault.postgres.TmailPostgresDeletedMessageMetadataVault;

public class TMailPostgresDeletedMessageVaultModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new TMailDeletedMessageVaultModule());

        Multibinder<PostgresDataDefinition> postgresDataDefinitions = Multibinder.newSetBinder(binder(), PostgresDataDefinition.class);
        postgresDataDefinitions.addBinding().toInstance(PostgresDeletedMessageMetadataDataDefinition.MODULE);

        bind(TmailDeletedMessageWithStorageInformationConverter.class).in(Scopes.SINGLETON);

        bind(TmailPostgresDeletedMessageMetadataVault.class).in(Scopes.SINGLETON);
        bind(DeletedMessageMetadataVault.class)
            .to(TmailPostgresDeletedMessageMetadataVault.class);

        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class, Names.named(CONTENT_DELETION))
            .addBinding()
            .to(DeletedMessageVaultDeletionListener.class);
    }
}
