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

package com.linagora.tmail.vault;

import org.apache.james.modules.vault.DeletedMessageVaultConfigurationModule;
import org.apache.james.vault.DeletedMessageVault;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.linagora.tmail.vault.blob.BlobIdTimeGenerator;
import com.linagora.tmail.vault.blob.BucketNameGenerator;
import com.linagora.tmail.vault.blob.TmailBlobStoreDeletedMessageVault;

public class TMailDeletedMessageVaultModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new DeletedMessageVaultConfigurationModule());
        install(new TMailVaultTaskSerializationModule());

        bind(BucketNameGenerator.class).in(Scopes.SINGLETON);
        bind(BlobIdTimeGenerator.class).in(Scopes.SINGLETON);
        bind(TmailBlobStoreDeletedMessageVault.class).in(Scopes.SINGLETON);
        bind(DeletedMessageVault.class)
            .to(TmailBlobStoreDeletedMessageVault.class);
    }
}
