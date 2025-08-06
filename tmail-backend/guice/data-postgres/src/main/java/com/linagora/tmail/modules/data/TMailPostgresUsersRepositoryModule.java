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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.modules.data;

import jakarta.inject.Named;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.backends.postgres.PostgresTable;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.lib.UsersDAO;
import org.apache.james.user.postgres.PostgresUsersDAO;
import org.apache.james.user.postgres.PostgresUsersRepository;
import org.apache.james.user.postgres.PostgresUsersRepositoryConfiguration;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition;

public class TMailPostgresUsersRepositoryModule extends AbstractModule {
    public static final String TMAIL_POSTGRES_USER = "tmailPostgresUser";

    public static AbstractModule USER_CONFIGURATION_MODULE = new AbstractModule() {
        @Provides
        @Singleton
        public PostgresUsersRepositoryConfiguration provideConfiguration(ConfigurationProvider configurationProvider) throws ConfigurationException {
            return PostgresUsersRepositoryConfiguration.from(
                configurationProvider.getConfiguration("usersrepository"));
        }
    };

    @Override
    public void configure() {
        bind(PostgresUsersRepository.class).in(Scopes.SINGLETON);
        bind(UsersRepository.class).to(PostgresUsersRepository.class);

        bind(PostgresUsersDAO.class).in(Scopes.SINGLETON);
        bind(UsersDAO.class).to(PostgresUsersDAO.class);
    }

    @ProvidesIntoSet
    InitializationOperation configureInitialization(ConfigurationProvider configurationProvider, PostgresUsersRepository usersRepository) {
        return InitilizationOperationBuilder
            .forClass(PostgresUsersRepository.class)
            .init(() -> usersRepository.configure(configurationProvider.getConfiguration("usersrepository")));
    }

    @Provides
    @Singleton
    @Named(TMAIL_POSTGRES_USER)
    public PostgresTable.CreateTableFunction provideCreateUserTableFunction() {
        return TMailPostgresUserDataDefinition.PostgresUserTable.defaultCreateUserTableFunction();
    }

    @ProvidesIntoSet
    public PostgresDataDefinition provideUserDataDefinition(@Named(TMAIL_POSTGRES_USER) PostgresTable.CreateTableFunction createUserTableFunction) {
        return TMailPostgresUserDataDefinition.userDataDefinition(createUserTableFunction);
    }
}
