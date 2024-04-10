package com.linagora.tmail.combined.identity;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.lib.UsersRepositoryImpl;

public class CombinedUsersRepository extends UsersRepositoryImpl<CombinedUserDAO> {

    @Inject
    public CombinedUsersRepository(DomainList domainList,
                                   CombinedUserDAO usersDAO) {
        super(domainList, usersDAO);
    }

    @Override
    public Optional<Username> test(Username name, String password) throws UsersRepositoryException {
        return usersDAO.test(name, password);
    }
}
