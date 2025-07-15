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

package com.linagora.tmail.user.cassandra;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.cassandra.CassandraUsersDAO;
import org.apache.james.user.lib.UsersRepositoryContract;
import org.apache.james.user.lib.UsersRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraUsersRepositoryTest {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(TMailCassandraUsersRepositoryDataDefinition.MODULE);

    @Nested
    class WhenEnableVirtualHosting implements UsersRepositoryContract.WithVirtualHostingContract {
        @RegisterExtension
        UserRepositoryExtension extension = UserRepositoryExtension.withVirtualHost();

        private UsersRepositoryImpl<CassandraUsersDAO> usersRepository;
        private TestSystem testSystem;

        @BeforeEach
        void setUp(TestSystem testSystem) throws Exception {
            usersRepository = getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting(), Optional.empty());
            this.testSystem = testSystem;
        }

        @Override
        public UsersRepositoryImpl<CassandraUsersDAO> testee() {
            return usersRepository;
        }

        @Override
        public UsersRepository testee(Optional<Username> administrator) throws Exception {
            return getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting(), administrator);
        }

        @Override
        public UsersRepository testee(Set<Username> administrators) throws Exception {
            return getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting(), administrators);
        }
    }

    @Nested
    class WhenDisableVirtualHosting implements UsersRepositoryContract.WithOutVirtualHostingContract {
        @RegisterExtension
        UserRepositoryExtension extension = UserRepositoryExtension.withoutVirtualHosting();

        private UsersRepositoryImpl<CassandraUsersDAO> usersRepository;
        private TestSystem testSystem;

        @BeforeEach
        void setUp(TestSystem testSystem) throws Exception {
            usersRepository = getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting(), Optional.empty());
            this.testSystem = testSystem;
        }

        @Override
        public UsersRepositoryImpl<CassandraUsersDAO> testee() {
            return usersRepository;
        }

        @Override
        public UsersRepository testee(Optional<Username> administrator) throws Exception {
            return getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting(), administrator);
        }

        @Override
        public UsersRepository testee(Set<Username> administrators) throws Exception {
            return getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting(), administrators);
        }
    }

    private static UsersRepositoryImpl<CassandraUsersDAO> getUsersRepository(DomainList domainList, boolean enableVirtualHosting, Optional<Username> administrator) throws Exception {
        CassandraUsersDAO usersDAO = new CassandraUsersDAO(cassandraCluster.getCassandraCluster().getConf());
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("enableVirtualHosting", String.valueOf(enableVirtualHosting));
        administrator.ifPresent(username -> configuration.addProperty("administratorId", username.asString()));

        UsersRepositoryImpl<CassandraUsersDAO> usersRepository = new UsersRepositoryImpl<>(domainList, usersDAO);
        usersRepository.configure(configuration);
        return usersRepository;
    }

    private static UsersRepositoryImpl<CassandraUsersDAO> getUsersRepository(DomainList domainList, boolean enableVirtualHosting, Set<Username> administrators) throws Exception {
        CassandraUsersDAO usersDAO = new CassandraUsersDAO(cassandraCluster.getCassandraCluster().getConf());
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("enableVirtualHosting", String.valueOf(enableVirtualHosting));
        administrators.forEach(admin -> configuration.addProperty("administratorIds.administratorId", admin.asString()));
        UsersRepositoryImpl<CassandraUsersDAO> usersRepository = new UsersRepositoryImpl<>(domainList, usersDAO);
        usersRepository.configure(configuration);
        return usersRepository;
    }
}
