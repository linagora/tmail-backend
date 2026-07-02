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

package com.linagora.tmail.migration.modules;

import org.apache.james.adapter.mailbox.DelegationStoreAuthorizator;
import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.webadmin.Routes;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.migration.core.MigratedUsersRepository;
import com.linagora.tmail.migration.postgres.MigratedUsersDataDefinition;
import com.linagora.tmail.migration.postgres.PostgresMigratedUsersRepository;
import com.linagora.tmail.migration.webadmin.MigratedUsersRoutes;

/**
 * Wires the migrated-users list: the PostgreSQL-backed repository, its table (auto-provisioned by the
 * server through the {@link PostgresDataDefinition} multibinder) and the {@code /migratedUsers}
 * webadmin endpoint.
 */
public class MigratedUsersModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(PostgresMigratedUsersRepository.class).in(Scopes.SINGLETON);
        bind(MigratedUsersRepository.class).to(PostgresMigratedUsersRepository.class);

        // Required by the SMTP CoreCmdHandlerLoader (UsersRepositoryAuthHook); backed by the DelegationStore.
        bind(Authorizator.class).to(DelegationStoreAuthorizator.class);

        Multibinder.newSetBinder(binder(), PostgresDataDefinition.class)
            .addBinding().toInstance(MigratedUsersDataDefinition.MODULE);

        Multibinder.newSetBinder(binder(), Routes.class)
            .addBinding().to(MigratedUsersRoutes.class);
    }
}
