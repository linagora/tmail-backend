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

package com.linagora.tmail.encrypted.postgres;

import org.apache.james.backends.postgres.PostgresDataDefinition;
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

        Multibinder<PostgresDataDefinition> postgresDataDefinitions = Multibinder.newSetBinder(binder(), PostgresDataDefinition.class);
        postgresDataDefinitions.addBinding().toInstance(com.linagora.tmail.encrypted.postgres.table.PostgresKeystoreModule.MODULE);

        Multibinder.newSetBinder(binder(), UsernameChangeTaskStep.class).addBinding().to(PGPKeysUsernameChangeTaskStep.class);
        Multibinder.newSetBinder(binder(), DeleteUserDataTaskStep.class).addBinding().to(PGPKeysUserDeletionTaskStep.class);
    }
}
