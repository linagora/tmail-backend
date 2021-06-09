package com.linagora.tmail.combined.identity;

import javax.inject.Inject;

import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.lib.UsersDAO;
import org.apache.james.utils.GuiceProbe;

public class UsersRepositoryClassProbe implements GuiceProbe {
    private final UsersRepository usersRepository;
    private final UsersDAO usersDAO;

    @Inject
    public UsersRepositoryClassProbe(UsersRepository usersRepository, UsersDAO usersDAO) {
        this.usersRepository = usersRepository;
        this.usersDAO = usersDAO;
    }

    public Class<? extends UsersRepository> getUserRepositoryClass() {
        return usersRepository.getClass();
    }

    public Class<? extends UsersDAO> getUsersDAOClass() {
        return usersDAO.getClass();
    }
}
