package com.linagora.tmail.combined.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.InvalidUsernameException;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.cassandra.CassandraUsersDAO;
import org.apache.james.user.lib.UsersRepositoryContract;
import org.apache.james.user.lib.model.Algorithm;
import org.apache.james.user.lib.model.DefaultUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public interface CombinedUsersRepositoryContract {

    class CombinedUserRepositoryExtension implements BeforeEachCallback, ParameterResolver {

        private static final boolean ENABLE_VIRTUAL_HOSTING = true;
        private static final boolean DISABLE_VIRTUAL_HOSTING = !ENABLE_VIRTUAL_HOSTING;

        public static CombinedUserRepositoryExtension withVirtualHost() {
            return new CombinedUserRepositoryExtension(ENABLE_VIRTUAL_HOSTING);
        }

        public static CombinedUserRepositoryExtension withoutVirtualHosting() {
            return new CombinedUserRepositoryExtension(DISABLE_VIRTUAL_HOSTING);
        }

        private final boolean supportVirtualHosting;
        private CombinedTestSystem combinedTestSystem;

        private CombinedUserRepositoryExtension(boolean supportVirtualHosting) {
            this.supportVirtualHosting = supportVirtualHosting;
        }

        @Override
        public void beforeEach(ExtensionContext extensionContext) throws Exception {
            combinedTestSystem = new CombinedTestSystem(supportVirtualHosting);
        }

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            return parameterContext.getParameter().getType() == CombinedTestSystem.class;
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            return combinedTestSystem;
        }

        public boolean isSupportVirtualHosting() {
            return supportVirtualHosting;
        }
    }

    class CombinedTestSystem {
        static final Domain DOMAIN = Domain.of("james.org");
        static final Domain UNKNOW_DOMAIN = Domain.of("unknown.org");

        private final boolean supportVirtualHosting;
        private final SimpleDomainList domainList;
        private final Username userAlreadyInLDAP;
        private final Username userAlreadyInLDAP2;
        private final Username userAlreadyInLDAP3;
        private final Username userAlreadyInLDAPCaseVariation;
        private final Username userNotAlreadyInLDAP;
        private final Username userWithUnknowDomain;
        private final Username invalidUsername;

        public CombinedTestSystem(boolean supportVirtualHosting) throws Exception {
            this.supportVirtualHosting = supportVirtualHosting;
            domainList = new SimpleDomainList();
            domainList.addDomain(DOMAIN);
            userAlreadyInLDAP = toUsername("james-user");
            userAlreadyInLDAP2 = toUsername("james-user2");
            userAlreadyInLDAP3 = toUsername("james-user3");
            userAlreadyInLDAPCaseVariation = toUsername("JaMes-UseR");
            userNotAlreadyInLDAP = toUsername("user-not-already-in-ldap");
            userWithUnknowDomain = toUsername("unknown", UNKNOW_DOMAIN);
            invalidUsername = toUsername("userContains)*(");
        }

        private Username toUsername(String login) {
            return toUsername(login, DOMAIN);
        }

        private Username toUsername(String login, Domain domain) {
            if (supportVirtualHosting) {
                return Username.fromLocalPartWithDomain(login, domain);
            } else {
                return Username.fromLocalPartWithoutDomain(login);
            }
        }

        public SimpleDomainList getDomainList() {
            return domainList;
        }
    }

    interface WithVirtualHostingContract extends UsersRepositoryContract.WithVirtualHostingReadOnlyContract, WithVirtualHostingReadWriteContract, ReadWriteContract, ModificationValidationContract {
    }

    interface WithOutVirtualHostingContract extends UsersRepositoryContract.WithOutVirtualHostingReadOnlyContract, ReadWriteContract {
    }

    /**
     * This class is rewrite from {@link UsersRepositoryContract.ReadWriteContract}
     */
    interface ReadWriteContract {
        CombinedUsersRepository testee();

        CassandraUsersDAO cassandraUsersDAO();

        @Test
        default void countUsersShouldReturnNumberOfUsersWhenNotEmptyRepository(CombinedTestSystem testSystem) throws UsersRepositoryException {
            List<Username> keys = Arrays.asList(
                testSystem.userAlreadyInLDAP,
                testSystem.userAlreadyInLDAP2,
                testSystem.userAlreadyInLDAP3);
            for (Username username : keys) {
                testee().addUser(username, username.asString());
            }
            assertThat(testee().countUsers()).isEqualTo(keys.size());
        }

        @Test
        default void listShouldReturnExactlyUsersInRepository(CombinedTestSystem testSystem) throws UsersRepositoryException {
            List<Username> keys = Arrays.asList(
                testSystem.userAlreadyInLDAP,
                testSystem.userAlreadyInLDAP2,
                testSystem.userAlreadyInLDAP3);
            for (Username username : keys) {
                testee().addUser(username, username.asString());
            }
            Iterator<Username> actual = testee().list();
            assertThat(actual)
                .toIterable()
                .containsOnly(testSystem.userAlreadyInLDAP,
                    testSystem.userAlreadyInLDAP2,
                    testSystem.userAlreadyInLDAP3);
        }

        @Test
        default void addUserShouldAddAUserWhenEmptyRepository(CombinedTestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.userAlreadyInLDAP, "PASSWORD");
            assertThat(testee().contains(testSystem.userAlreadyInLDAP)).isTrue();
        }

        @Test
        default void containsShouldPreserveCaseVariation(CombinedTestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.userAlreadyInLDAPCaseVariation, "PASSWORD");
            assertThat(testee().contains(testSystem.userAlreadyInLDAPCaseVariation)).isTrue();
        }

        @Test
        default void containsShouldBeCaseInsensitive(CombinedTestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.userAlreadyInLDAPCaseVariation, "password2");

            assertThat(testee().contains(testSystem.userAlreadyInLDAP)).isTrue();
        }

        @Test
        default void containsShouldBeCaseInsensitiveWhenOriginalValueLowerCased(CombinedTestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.userAlreadyInLDAP, "password2");

            assertThat(testee().contains(testSystem.userAlreadyInLDAPCaseVariation)).isTrue();
        }

        @Test
        default void addUserShouldDisableCaseVariationWhenOriginalValueLowerCased(CombinedTestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.userAlreadyInLDAP, "password2");

            assertThatThrownBy(() -> testee().addUser(testSystem.userAlreadyInLDAPCaseVariation, "pass"))
                .isInstanceOf(UsersRepositoryException.class);
        }

        @Test
        default void addUserShouldDisableCaseVariation(CombinedTestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.userAlreadyInLDAPCaseVariation, "password2");

            assertThatThrownBy(() -> testee().addUser(testSystem.userAlreadyInLDAP, "pass"))
                .isInstanceOf(UsersRepositoryException.class);
        }

        @Test
        default void listShouldReturnLowerCaseUser(CombinedTestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.userAlreadyInLDAPCaseVariation, "password2");

            assertThat(testee().list())
                .toIterable()
                .containsExactly(testSystem.userAlreadyInLDAP);
        }

        @Test
        default void removeUserShouldBeCaseInsentiveOnCaseVariationUser(CombinedTestSystem testSystem) throws UsersRepositoryException {
            cassandraUsersDAO().addUser(testSystem.toUsername("uSer-NeeD-ReMOVE"), "password2");

            testee().removeUser(testSystem.toUsername("user-need-remove"));

            assertThat(testee().list())
                .toIterable()
                .isEmpty();
        }

        @Test
        default void removeUserShouldBeCaseInsentive(CombinedTestSystem testSystem) throws UsersRepositoryException {
            cassandraUsersDAO().addUser(testSystem.toUsername("user-need-remove"), "password2");

            testee().removeUser(testSystem.toUsername("uSer-NeeD-ReMOVE"));

            assertThat(testee().list())
                .toIterable()
                .isEmpty();
        }

        @Test
        default void getUserByNameShouldBeCaseInsentive(CombinedTestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.userAlreadyInLDAP, "password2");

            assertThat(testee().getUserByName(testSystem.userAlreadyInLDAPCaseVariation).getUserName())
                .isEqualTo(testSystem.userAlreadyInLDAP);
        }

        @Test
        default void getUserByNameShouldReturnLowerCaseAddedUser(CombinedTestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.userAlreadyInLDAPCaseVariation, "password2");

            assertThat(testee().getUserByName(testSystem.userAlreadyInLDAP).getUserName())
                .isEqualTo(testSystem.userAlreadyInLDAP);
        }

        @Test
        default void testShouldBeCaseInsentiveOnCaseVariationUser(CombinedTestSystem testSystem) throws UsersRepositoryException {
            String password = "secret";
            testee().addUser(testSystem.userAlreadyInLDAPCaseVariation, password);

            assertThat(testee().test(testSystem.userAlreadyInLDAP, password))
                .isTrue();
        }

        @Test
        default void testShouldBeCaseInsentive(CombinedTestSystem testSystem) throws UsersRepositoryException {
            String password = "secret";
            testee().addUser(testSystem.userAlreadyInLDAP, password);

            assertThat(testee().test(testSystem.userAlreadyInLDAPCaseVariation, password))
                .isTrue();
        }

        @Test
        default void addUserShouldAddAUserWhenNotEmptyRepository(CombinedTestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.userAlreadyInLDAP2, "password2");
            //When
            testee().addUser(testSystem.userAlreadyInLDAP3, "password3");
            //Then
            assertThat(testee().contains(testSystem.userAlreadyInLDAP2)).isTrue();
        }

        @Test
        default void addUserShouldThrowWhenSameUsernameWithDifferentCase(CombinedTestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.userAlreadyInLDAP, "password");
            //When
            assertThatThrownBy(() -> testee().addUser(testSystem.userAlreadyInLDAPCaseVariation, "password"))
                .isInstanceOf(AlreadyExistInUsersRepositoryException.class);
        }

        @Test
        default void addUserShouldThrowWhenUserAlreadyPresentInRepository(CombinedTestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.userAlreadyInLDAP, "password");
            //When
            assertThatThrownBy(() -> testee().addUser(testSystem.userAlreadyInLDAP, "password2"))
                .isInstanceOf(AlreadyExistInUsersRepositoryException.class);
        }

        @Test
        default void getUserByNameShouldReturnAUserWhenContainedInRepository(CombinedTestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.userAlreadyInLDAP, "password");
            //When
            User actual = testee().getUserByName(testSystem.userAlreadyInLDAP);
            //Then
            assertThat(actual).isNotNull();
            assertThat(actual.getUserName()).isEqualTo(testSystem.userAlreadyInLDAP);
        }

        @Test
        default void getUserByNameShouldReturnUserWhenDifferentCase(CombinedTestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.userAlreadyInLDAP, "password");
            //When
            User actual = testee().getUserByName(testSystem.userAlreadyInLDAPCaseVariation);
            //Then
            assertThat(actual).isNotNull();
            assertThat(actual.getUserName()).isEqualTo(testSystem.userAlreadyInLDAP);
        }

        @Test
        default void testShouldReturnTrueWhenAUserHasACorrectPassword(CombinedTestSystem testSystem) throws UsersRepositoryException {
            boolean actual = testee().test(testSystem.userAlreadyInLDAP, "secret");
            assertThat(actual).isTrue();
        }

        @Test
        default void testShouldReturnFalseWhenAUserHasAnIncorrectPassword(CombinedTestSystem testSystem) throws UsersRepositoryException {
            boolean actual = testee().test(testSystem.userAlreadyInLDAP, "password2");
            assertThat(actual).isFalse();
        }

        @Test
        default void testShouldReturnFalseWhenAUserHasAnIncorrectCasePassword(CombinedTestSystem testSystem) throws UsersRepositoryException {
            boolean actual = testee().test(testSystem.userAlreadyInLDAP, "Secret");

            assertThat(actual).isFalse();
        }

        @Test
        default void testShouldReturnFalseWhenAUserIsNotInRepository(CombinedTestSystem testSystem) throws UsersRepositoryException {
            boolean actual = testee().test(testSystem.userNotAlreadyInLDAP, "secret");
            assertThat(actual).isFalse();
        }

        @Test
        default void testShouldReturnTrueWhenAUserHasAnIncorrectCaseName(CombinedTestSystem testSystem) throws UsersRepositoryException {
            boolean actual = testee().test(testSystem.userAlreadyInLDAPCaseVariation, "secret");

            assertThat(actual).isTrue();
        }

        @Test
        default void removeUserShouldRemoveAUserWhenPresentInRepository(CombinedTestSystem testSystem) throws UsersRepositoryException {
            cassandraUsersDAO().addUser(testSystem.userNotAlreadyInLDAP, "password");
            testee().removeUser(testSystem.userNotAlreadyInLDAP);

            assertThat(testee().contains(testSystem.userNotAlreadyInLDAP)).isFalse();
        }

        @Test
        default void removeUserShouldThrowWhenUserNotInRepository(CombinedTestSystem testSystem) {
            //When
            assertThatThrownBy(() -> testee().removeUser(testSystem.toUsername("user-not-exit")))
                .isInstanceOf(UsersRepositoryException.class);
        }

    }

    /**
     * This class is rewrite from {@link UsersRepositoryContract.WithVirtualHostingReadWriteContract}
     */
    interface WithVirtualHostingReadWriteContract {
        CombinedUsersRepository testee();

        @Test
        default void addUserShouldThrowWhenUserDoesNotBelongToDomainList(CombinedTestSystem testSystem) {
            assertThatThrownBy(() -> testee().addUser(testSystem.userWithUnknowDomain, "password"))
                .isInstanceOf(InvalidUsernameException.class)
                .hasMessage("Domain does not exist in DomainList");
        }

        @Test
        default void addUserShouldThrowWhenInvalidUser(CombinedTestSystem testSystem) {
            assertThatThrownBy(() -> testee().addUser(testSystem.invalidUsername, "password"))
                .isInstanceOf(InvalidUsernameException.class)
                .hasMessageContaining("should not contain any of those characters");
        }

        @Test
        default void updateUserShouldThrowWhenUserDoesNotBelongToDomainList(CombinedTestSystem testSystem) {
            assertThatThrownBy(() -> testee().updateUser(new DefaultUser(testSystem.userWithUnknowDomain, Algorithm.of("hasAlg"), Algorithm.of("hasAlg"))))
                .isInstanceOf(InvalidUsernameException.class)
                .hasMessage("Domain does not exist in DomainList");
        }

        @Test
        default void updateUserShouldNotThrowInvalidUsernameExceptionWhenInvalidUser(CombinedTestSystem testSystem) {
            assertThatThrownBy(() -> testee().updateUser(new DefaultUser(testSystem.invalidUsername, Algorithm.of("hasAlg"), Algorithm.of("hasAlg"))))
                .isNotInstanceOf(InvalidUsernameException.class);
        }

        @Test
        default void removeUserShouldThrowWhenUserDoesNotBelongToDomainList(CombinedTestSystem testSystem) {
            assertThatThrownBy(() -> testee().removeUser(testSystem.userWithUnknowDomain))
                .isInstanceOf(InvalidUsernameException.class)
                .hasMessage("Domain does not exist in DomainList");
        }

        @Test
        default void removeUserShouldNotThrowInvalidUsernameExceptionWhenInvalidUser(CombinedTestSystem testSystem) {
            assertThatThrownBy(() -> testee().removeUser(testSystem.invalidUsername))
                .isNotInstanceOf(InvalidUsernameException.class);
        }
    }

    interface ModificationValidationContract {
        CombinedUsersRepository testee();

        CassandraUsersDAO cassandraUsersDAO();

        @Test
        default void addUserShouldThrowWhenUserIsNotExitsInLDAP(CombinedTestSystem testSystem) {
            assertThatThrownBy(() -> testee().addUser(testSystem.userNotAlreadyInLDAP, "PASSWORD"))
                .isInstanceOf(UsersRepositoryException.class)
                .hasMessageContaining("Can not add user as it does not exits in LDAP repository.");
        }

        @Test
        default void updateUserShouldThrowEvenUserIsExits(CombinedTestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.userAlreadyInLDAP, "password");
            User user = testee().getUserByName(testSystem.userAlreadyInLDAP);
            user.setPassword("newpass");

            assertThatThrownBy(() -> testee().updateUser(user))
                .isInstanceOf(UsersRepositoryException.class)
                .hasMessageContaining("updateUser method is unsupported.");
        }

        @Test
        default void updateUserShouldThrow(CombinedTestSystem testSystem) {
            User user = new DefaultUser(testSystem.toUsername("user-not-exit"), Algorithm.of("hasAlg"), Algorithm.of("hasAlg"));

            assertThatThrownBy(() -> testee().updateUser(user))
                .isInstanceOf(UsersRepositoryException.class)
                .hasMessageContaining("updateUser method is unsupported.");
        }

        @Test
        default void removeUserShouldFailWhenLDAPUserIsExits(CombinedTestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.userAlreadyInLDAP, "password");

            assertThatThrownBy(() -> testee().removeUser(testSystem.userAlreadyInLDAP))
                .isInstanceOf(UsersRepositoryException.class)
                .hasMessageContaining("Can not remove user as it does still exit in LDAP repository.");
        }

        @Test
        default void removeUserShouldSuccessWhenLDAPUserIsNotExits(CombinedTestSystem testSystem) throws UsersRepositoryException {
            cassandraUsersDAO().addUser(testSystem.userNotAlreadyInLDAP, "password");
            testee().removeUser(testSystem.userNotAlreadyInLDAP);

            assertThat(testee().contains(testSystem.userNotAlreadyInLDAP)).isFalse();
        }

        @Test
        default void testShouldReturnTrueWhenLDAPPasswordIsCorrect(CombinedTestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.userAlreadyInLDAP, "password123");
            assertThat(testee().test(testSystem.userAlreadyInLDAP, "secret")).isTrue();
        }

        @Test
        default void testShouldReturnFalseWhenLDAPPasswordIsIncorrect(CombinedTestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.userAlreadyInLDAP, "password123");
            assertThat(testee().test(testSystem.userAlreadyInLDAP, "password123")).isFalse();
        }
    }

}
