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

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.adapter.mailbox.DelegationStoreAuthorizator;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.user.api.DelegationStore;
import org.apache.james.user.api.DelegationUsernameChangeTaskStep;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.apache.james.user.cassandra.CassandraDelegationStore;
import org.apache.james.user.cassandra.CassandraRepositoryConfiguration;
import org.apache.james.user.cassandra.CassandraUsersDAO;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition;

public class TMailCassandraDelegationStoreModule extends AbstractModule {
    @Override
    public void configure() {
        bind(DelegationStore.class).to(CassandraDelegationStore.class);
        bind(CassandraDelegationStore.UserExistencePredicate.class).to(CassandraDelegationStore.UserExistencePredicateImplementation.class);
        bind(Authorizator.class).to(DelegationStoreAuthorizator.class);
        Multibinder<CassandraDataDefinition> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraDataDefinition.class);
        cassandraDataDefinitions.addBinding().toInstance(TMailCassandraUsersRepositoryDataDefinition.MODULE);

        Multibinder.newSetBinder(binder(), UsernameChangeTaskStep.class)
            .addBinding().to(DelegationUsernameChangeTaskStep.class);
        bind(CassandraUsersDAO.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    public CassandraRepositoryConfiguration provideConfiguration(ConfigurationProvider configurationProvider) throws ConfigurationException {
        return CassandraRepositoryConfiguration.from(
            configurationProvider.getConfiguration("usersrepository"));
    }
}
