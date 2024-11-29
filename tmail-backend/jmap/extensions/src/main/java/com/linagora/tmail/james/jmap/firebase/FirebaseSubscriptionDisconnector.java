package com.linagora.tmail.james.jmap.firebase;

import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.james.core.Disconnector;
import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepository;

import reactor.core.publisher.Flux;

public class FirebaseSubscriptionDisconnector implements Disconnector {

    private final UsersRepository usersRepository;
    private final FirebaseSubscriptionRepository repository;

    @Inject
    public FirebaseSubscriptionDisconnector(UsersRepository usersRepository, FirebaseSubscriptionRepository repository) {
        this.usersRepository = usersRepository;
        this.repository = repository;
    }

    @Override
    public void disconnect(Predicate<Username> username) {
        Flux.from(usersRepository.listReactive())
            .filter(username)
            .flatMap(repository::revoke)
            .then()
            .block();
    }
}
