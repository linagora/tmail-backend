package com.linagora.tmail.combined.identity;

import java.util.Iterator;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.cassandra.CassandraUsersDAO;
import org.apache.james.user.ldap.ReadOnlyLDAPUsersDAO;
import org.apache.james.user.lib.UsersDAO;
import org.slf4j.LoggerFactory;

public class CombinedUserDAO implements UsersDAO {
    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(CombinedUserDAO.class);

    private final ReadOnlyLDAPUsersDAO readOnlyLDAPUsersDAO;
    private final CassandraUsersDAO cassandraUsersDAO;

    @Inject
    public CombinedUserDAO(ReadOnlyLDAPUsersDAO readOnlyLDAPUsersDAO,
                           CassandraUsersDAO cassandraUsersDAO) {
        this.readOnlyLDAPUsersDAO = readOnlyLDAPUsersDAO;
        this.cassandraUsersDAO = cassandraUsersDAO;
    }

    @Override
    public void addUser(Username username, String password) throws UsersRepositoryException {
        if (readOnlyLDAPUsersDAO.contains(username)) {
            cassandraUsersDAO.addUser(username, password);
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
            cassandraUsersDAO.removeUser(name);
        } else {
            throw new UsersRepositoryException("Can not remove user as it does still exit in LDAP repository. " + name.asString());
        }
    }

    @Override
    public Optional<? extends User> getUserByName(Username name) throws UsersRepositoryException {
        return cassandraUsersDAO.getUserByName(name);
    }

    @Override
    public boolean contains(Username name) throws UsersRepositoryException {
        return cassandraUsersDAO.contains(name);
    }

    @Override
    public int countUsers() throws UsersRepositoryException {
        return cassandraUsersDAO.countUsers();
    }

    @Override
    public Iterator<Username> list() throws UsersRepositoryException {
        return cassandraUsersDAO.list();
    }

    public boolean test(Username name, String password) throws UsersRepositoryException {
        return readOnlyLDAPUsersDAO.getUserByName(name)
            .map(user -> user.verifyPassword(password))
            .orElseGet(() -> {
                LOGGER.info("Could not retrieve user {}. Password is unverified.", name);
                return false;
            });
    }
}
