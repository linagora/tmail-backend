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

package com.linagora.tmail.user.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.lib.UsersRepositoryContract;
import org.apache.james.user.lib.UsersRepositoryImpl;
import org.apache.james.user.postgres.PostgresUsersDAO;
import org.apache.james.user.postgres.PostgresUsersRepository;
import org.apache.james.user.postgres.PostgresUsersRepositoryConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class PostgresUsersRepositoryTest {

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(TMailPostgresUserDataDefinition.MODULE);

    @Nested
    class WhenEnableVirtualHosting implements UsersRepositoryContract.WithVirtualHostingContract {
        @RegisterExtension
        UserRepositoryExtension extension = UserRepositoryExtension.withVirtualHost();

        private UsersRepositoryImpl<PostgresUsersDAO> usersRepository;
        private TestSystem testSystem;

        @BeforeEach
        void setUp(TestSystem testSystem) throws Exception {
            usersRepository = getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting(), Optional.empty());
            this.testSystem = testSystem;
        }

        @Override
        public UsersRepositoryImpl<PostgresUsersDAO> testee() {
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

        @Test
        void listUsersReactiveThenExecuteOtherPostgresQueriesShouldNotHang() throws Exception {
            Domain domain = Domain.of("example.com");
            testSystem.getDomainList().addDomain(domain);

            Flux.range(1, 1000)
                .flatMap(counter -> Mono.fromRunnable(Throwing.runnable(() -> usersRepository.addUser(Username.fromLocalPartWithDomain(counter.toString(), domain), "password"))),
                    128)
                .collectList()
                .block();

            assertThat(Flux.from(usersRepository.listReactive())
                .flatMap(username -> Mono.fromCallable(() -> usersRepository.test(username, "password")
                    .orElseThrow(() -> new RuntimeException("Wrong user credential")))
                    .subscribeOn(Schedulers.boundedElastic()))
                .collectList()
                .block())
                .hasSize(1000);
        }
    }

    @Nested
    class WhenDisableVirtualHosting implements UsersRepositoryContract.WithOutVirtualHostingContract {
        @RegisterExtension
        UserRepositoryExtension extension = UserRepositoryExtension.withoutVirtualHosting();

        private UsersRepositoryImpl<PostgresUsersDAO> usersRepository;
        private TestSystem testSystem;

        @BeforeEach
        void setUp(TestSystem testSystem) throws Exception {
            usersRepository = getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting(), Optional.empty());
            this.testSystem = testSystem;
        }

        @Override
        public UsersRepositoryImpl<PostgresUsersDAO> testee() {
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

    private static UsersRepositoryImpl<PostgresUsersDAO> getUsersRepository(DomainList domainList, boolean enableVirtualHosting, Optional<Username> administrator) throws Exception {
        Set<Username> administrators = administrator.map(Set::of)
            .orElse(Set.of());

        return getUsersRepository(domainList, enableVirtualHosting, administrators);
    }

    private static UsersRepositoryImpl<PostgresUsersDAO> getUsersRepository(DomainList domainList, boolean enableVirtualHosting, Set<Username> administrators) throws Exception {
        PostgresUsersDAO usersDAO = new PostgresUsersDAO(postgresExtension.getDefaultPostgresExecutor(),
            PostgresUsersRepositoryConfiguration.DEFAULT);
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("enableVirtualHosting", String.valueOf(enableVirtualHosting));
        administrators.forEach(admin -> configuration.addProperty("administratorIds.administratorId", admin.asString()));

        UsersRepositoryImpl<PostgresUsersDAO> usersRepository = new PostgresUsersRepository(domainList, usersDAO);
        usersRepository.configure(configuration);
        return usersRepository;
    }
}
