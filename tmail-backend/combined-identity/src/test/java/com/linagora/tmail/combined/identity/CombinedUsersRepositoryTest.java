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

import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN;
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_LOCAL_PART;
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_PASSWORD;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.cassandra.CassandraRepositoryConfiguration;
import org.apache.james.user.cassandra.CassandraUsersDAO;
import org.apache.james.user.cassandra.CassandraUsersRepositoryModule;
import org.apache.james.user.ldap.DockerLdapSingleton;
import org.apache.james.user.ldap.LDAPConnectionFactory;
import org.apache.james.user.ldap.LdapGenericContainer;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.james.user.ldap.ReadOnlyLDAPUsersDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.combined.identity.CombinedUsersRepositoryContract.CombinedTestSystem;

public class CombinedUsersRepositoryTest {

    static LdapGenericContainer ldapContainer = DockerLdapSingleton.ldapContainer;

    @BeforeAll
    static void setUpAll() {
        ldapContainer.start();
    }

    @AfterAll
    static void afterAll() {
        ldapContainer.stop();
    }

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraUsersRepositoryModule.MODULE);

    @Nested
    class WhenEnableVirtualHosting implements CombinedUsersRepositoryContract.WithVirtualHostingContract {
        @RegisterExtension
        UserRepositoryExtension extension = UserRepositoryExtension.withVirtualHost();
        @RegisterExtension
        CombinedUsersRepositoryContract.CombinedUserRepositoryExtension combinedExtension = CombinedUsersRepositoryContract.CombinedUserRepositoryExtension.withVirtualHost();

        private CombinedUsersRepository combinedUsersRepository;
        private ReadOnlyLDAPUsersDAO readOnlyLDAPUsersDAO;
        private CassandraUsersDAO cassandraUsersDAO;
        private CombinedTestSystem testSystem;

        @BeforeEach
        void setUp(CombinedTestSystem testSystem) throws Exception {
            this.testSystem = testSystem;
            HierarchicalConfiguration<ImmutableNode> config = ldapRepositoryConfiguration(ldapContainer, true);
            LdapRepositoryConfiguration ldapConfiguration = LdapRepositoryConfiguration.from(config);
            readOnlyLDAPUsersDAO = new ReadOnlyLDAPUsersDAO(new NoopGaugeRegistry(), new LDAPConnectionFactory(ldapConfiguration).getLdapConnectionPool(),
                ldapConfiguration);
            readOnlyLDAPUsersDAO.configure(config);
            readOnlyLDAPUsersDAO.init();

            cassandraUsersDAO = new CassandraUsersDAO(cassandraCluster.getCassandraCluster().getConf(), CassandraRepositoryConfiguration.DEFAULT);
            combinedUsersRepository = getUsersRepository(new CombinedUserDAO(readOnlyLDAPUsersDAO, cassandraUsersDAO), testSystem.getDomainList(), true, Optional.empty());
        }

        @Override
        public CombinedUsersRepository testee() {
            return combinedUsersRepository;
        }

        @Override
        public UsersRepository testee(Optional<Username> administrator) throws Exception {
            return getUsersRepository(new CombinedUserDAO(readOnlyLDAPUsersDAO, cassandraUsersDAO), testSystem.getDomainList(), extension.isSupportVirtualHosting(), administrator);
        }

        @Override
        public UsersRepository testee(Set<Username> administrators) throws Exception {
            return getUsersRepository(new CombinedUserDAO(readOnlyLDAPUsersDAO, cassandraUsersDAO), testSystem.getDomainList(), extension.isSupportVirtualHosting(), administrators);
        }

        @Override
        public CassandraUsersDAO cassandraUsersDAO() {
            return cassandraUsersDAO;
        }
    }

    @Nested
    class WhenDisableVirtualHosting implements CombinedUsersRepositoryContract.WithOutVirtualHostingContract {
        @RegisterExtension
        UserRepositoryExtension extension = UserRepositoryExtension.withoutVirtualHosting();
        @RegisterExtension
        CombinedUsersRepositoryContract.CombinedUserRepositoryExtension combinedExtension = CombinedUsersRepositoryContract.CombinedUserRepositoryExtension.withoutVirtualHosting();

        private CombinedUsersRepository combinedUsersRepository;
        private ReadOnlyLDAPUsersDAO readOnlyLDAPUsersDAO;
        private CassandraUsersDAO cassandraUsersDAO;
        private CombinedTestSystem testSystem;

        @BeforeEach
        void setUp(CombinedTestSystem testSystem) throws Exception {
            this.testSystem = testSystem;
            HierarchicalConfiguration<ImmutableNode> config = ldapRepositoryConfiguration(ldapContainer, false);
            LdapRepositoryConfiguration ldapConfiguration = LdapRepositoryConfiguration.from(config);
            readOnlyLDAPUsersDAO = new ReadOnlyLDAPUsersDAO(new NoopGaugeRegistry(), new LDAPConnectionFactory(ldapConfiguration).getLdapConnectionPool(),
                ldapConfiguration);
            readOnlyLDAPUsersDAO.configure(config);
            readOnlyLDAPUsersDAO.init();

            cassandraUsersDAO = new CassandraUsersDAO(cassandraCluster.getCassandraCluster().getConf(), CassandraRepositoryConfiguration.DEFAULT);

            combinedUsersRepository = getUsersRepository(new CombinedUserDAO(readOnlyLDAPUsersDAO, cassandraUsersDAO), testSystem.getDomainList(), false, Optional.empty());
        }

        @Override
        public CombinedUsersRepository testee() {
            return combinedUsersRepository;
        }

        @Override
        public UsersRepository testee(Optional<Username> administrator) throws Exception {
            return getUsersRepository(new CombinedUserDAO(readOnlyLDAPUsersDAO, cassandraUsersDAO), testSystem.getDomainList(), extension.isSupportVirtualHosting(), administrator);
        }

        @Override
        public UsersRepository testee(Set<Username> administrators) throws Exception {
            return getUsersRepository(new CombinedUserDAO(readOnlyLDAPUsersDAO, cassandraUsersDAO), testSystem.getDomainList(), extension.isSupportVirtualHosting(), administrators);
        }

        @Override
        public CassandraUsersDAO cassandraUsersDAO() {
            return cassandraUsersDAO;
        }
    }

    private static CombinedUsersRepository getUsersRepository(CombinedUserDAO combinedUserDAO,
                                                              DomainList domainList,
                                                              boolean enableVirtualHosting,
                                                              Optional<Username> administrator) throws Exception {
        CombinedUsersRepository repository = new CombinedUsersRepository(domainList, combinedUserDAO);
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("enableVirtualHosting", String.valueOf(enableVirtualHosting));
        administrator.ifPresent(username -> configuration.addProperty("administratorId", username.asString()));
        repository.configure(configuration);
        return repository;
    }

    private static CombinedUsersRepository getUsersRepository(CombinedUserDAO combinedUserDAO,
                                                              DomainList domainList,
                                                              boolean enableVirtualHosting,
                                                              Set<Username> administrators) throws Exception {
        CombinedUsersRepository repository = new CombinedUsersRepository(domainList, combinedUserDAO);
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("enableVirtualHosting", String.valueOf(enableVirtualHosting));
        administrators.forEach(admin -> configuration.addProperty("administratorIds.administratorId", admin.asString()));
        repository.configure(configuration);
        return repository;
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfiguration(LdapGenericContainer ldapContainer, boolean enableVirtualHosting) {
        PropertyListConfiguration configuration = baseConfiguration(ldapContainer);
        if (enableVirtualHosting) {
            configuration.addProperty("[@userIdAttribute]", "mail");
            configuration.addProperty("supportsVirtualHosting", true);
            configuration.addProperty("[@administratorId]", ADMIN.asString());
        } else {
            configuration.addProperty("[@userIdAttribute]", "uid");
            configuration.addProperty("[@administratorId]", ADMIN_LOCAL_PART);
        }
        return configuration;
    }

    private static PropertyListConfiguration baseConfiguration(LdapGenericContainer ldapContainer) {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("[@ldapHost]", ldapContainer.getLdapHost());
        configuration.addProperty("[@principal]", "cn=admin,dc=james,dc=org");
        configuration.addProperty("[@credentials]", ADMIN_PASSWORD);
        configuration.addProperty("[@userBase]", "ou=People,dc=james,dc=org");
        configuration.addProperty("[@userObjectClass]", "inetOrgPerson");
        configuration.addProperty("[@maxRetries]", "1");
        configuration.addProperty("[@retryStartInterval]", "0");
        configuration.addProperty("[@retryMaxInterval]", "2");
        configuration.addProperty("[@retryIntervalScale]", "1000");
        configuration.addProperty("[@connectionTimeout]", "1000");
        configuration.addProperty("[@readTimeout]", "1000");
        return configuration;
    }

}
