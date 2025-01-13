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

import java.util.Iterator;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.ldap.ReadOnlyLDAPUsersDAO;
import org.apache.james.user.lib.UsersDAO;
import org.reactivestreams.Publisher;
import org.slf4j.LoggerFactory;

public class CombinedUserDAO implements UsersDAO {
    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(CombinedUserDAO.class);
    public static final String DATABASE_INJECT_NAME = "database";

    private final ReadOnlyLDAPUsersDAO readOnlyLDAPUsersDAO;
    private final UsersDAO usersDAO;

    @Inject
    @Singleton
    public CombinedUserDAO(ReadOnlyLDAPUsersDAO readOnlyLDAPUsersDAO,
                           @Named(DATABASE_INJECT_NAME) UsersDAO usersDAO) {
        this.readOnlyLDAPUsersDAO = readOnlyLDAPUsersDAO;
        this.usersDAO = usersDAO;
    }

    @Override
    public void addUser(Username username, String password) throws UsersRepositoryException {
        if (readOnlyLDAPUsersDAO.contains(username)) {
            usersDAO.addUser(username, password);
        } else {
            throw new UsersRepositoryException("Can not add user as it does not exits in LDAP repository. " + username.asString());
        }
    }

    @Override
    public void updateUser(User user) throws UsersRepositoryException {
        throw new UsersRepositoryException("updateUser method is unsupported.");
    }

    @Override
    public void removeUser(Username name) throws UsersRepositoryException {
        if (!readOnlyLDAPUsersDAO.contains(name)) {
            usersDAO.removeUser(name);
        } else {
            throw new UsersRepositoryException("Can not remove user as it does still exit in LDAP repository. " + name.asString());
        }
    }

    @Override
    public Optional<? extends User> getUserByName(Username name) throws UsersRepositoryException {
        return usersDAO.getUserByName(name);
    }

    @Override
    public boolean contains(Username name) throws UsersRepositoryException {
        return usersDAO.contains(name);
    }

    @Override
    public Publisher<Boolean> containsReactive(Username name) {
        return usersDAO.containsReactive(name);
    }

    @Override
    public int countUsers() throws UsersRepositoryException {
        return usersDAO.countUsers();
    }

    @Override
    public Iterator<Username> list() throws UsersRepositoryException {
        return usersDAO.list();
    }

    @Override
    public Publisher<Username> listReactive() {
        return usersDAO.listReactive();
    }

    public Optional<Username> test(Username name, String password) throws UsersRepositoryException {
        return readOnlyLDAPUsersDAO.getUserByName(name)
                .filter(user -> user.verifyPassword(password))
                .map(User::getUserName)
                .or(() -> {
                    LOGGER.info("Could not retrieve user {}. Password is unverified.", name);
                    return Optional.empty();
                });
    }
}
