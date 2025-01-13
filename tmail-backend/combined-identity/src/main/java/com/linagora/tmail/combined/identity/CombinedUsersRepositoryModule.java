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

package com.linagora.tmail.combined.identity;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.ldap.LDAPConnectionFactory;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.james.user.ldap.ReadOnlyLDAPUsersDAO;
import org.apache.james.user.ldap.ReadOnlyUsersLDAPRepository;
import org.apache.james.user.lib.UsersDAO;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;

public class CombinedUsersRepositoryModule extends AbstractModule {
    @Override
    public void configure() {
        bind(ReadOnlyLDAPUsersDAO.class).in(Scopes.SINGLETON);
        bind(CombinedUserDAO.class).in(Scopes.SINGLETON);
        bind(CombinedUsersRepository.class).in(Scopes.SINGLETON);

        bind(UsersDAO.class).to(CombinedUserDAO.class);
        bind(UsersRepository.class).to(CombinedUsersRepository.class);
    }

    @Provides
    @Singleton
    public LdapRepositoryConfiguration provideConfiguration(ConfigurationProvider configurationProvider) throws ConfigurationException {
        return LdapRepositoryConfiguration.from(
            configurationProvider.getConfiguration("usersrepository"));
    }

    @Provides
    @Singleton
    public LDAPConnectionPool provideConfiguration(LdapRepositoryConfiguration configuration) throws LDAPException {
        return new LDAPConnectionFactory(configuration).getLdapConnectionPool();
    }

    @ProvidesIntoSet
    InitializationOperation configureUsersRepository(ConfigurationProvider configurationProvider, CombinedUsersRepository usersRepository) {
        return InitilizationOperationBuilder
            .forClass(CombinedUsersRepository.class)
            .init(() -> usersRepository.configure(configurationProvider.getConfiguration("usersrepository")));
    }

    @ProvidesIntoSet
    InitializationOperation configureLdap(ConfigurationProvider configurationProvider, ReadOnlyLDAPUsersDAO readOnlyLDAPUsersDAO) {
        return InitilizationOperationBuilder
            .forClass(ReadOnlyUsersLDAPRepository.class)
            .init(() -> {
                readOnlyLDAPUsersDAO.configure(configurationProvider.getConfiguration("usersrepository"));
                readOnlyLDAPUsersDAO.init();
            });
    }
}
