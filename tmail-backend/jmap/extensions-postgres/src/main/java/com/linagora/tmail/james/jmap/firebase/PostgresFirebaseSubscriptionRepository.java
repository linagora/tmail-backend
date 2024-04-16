package com.linagora.tmail.james.jmap.firebase;

import static com.linagora.tmail.james.jmap.firebase.FirebaseSubscriptionHelper.evaluateExpiresTime;
import static com.linagora.tmail.james.jmap.firebase.FirebaseSubscriptionHelper.isInThePast;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.change.TypeStateFactory;
import org.apache.james.jmap.api.model.TypeName;

import com.linagora.tmail.james.jmap.model.ExpireTimeInvalidException;
import com.linagora.tmail.james.jmap.model.FirebaseSubscription;
import com.linagora.tmail.james.jmap.model.FirebaseSubscriptionCreationRequest;
import com.linagora.tmail.james.jmap.model.FirebaseSubscriptionExpiredTime;
import com.linagora.tmail.james.jmap.model.FirebaseSubscriptionId;
import com.linagora.tmail.james.jmap.model.FirebaseSubscriptionNotFoundException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;

public class PostgresFirebaseSubscriptionRepository implements FirebaseSubscriptionRepository {
    private final Clock clock;
    private final TypeStateFactory typeStateFactory;
    private final PostgresExecutor.Factory executorFactory;

    @Inject
    @Singleton
    public PostgresFirebaseSubscriptionRepository(Clock clock, TypeStateFactory typeStateFactory, PostgresExecutor.Factory executorFactory) {
        this.clock = clock;
        this.typeStateFactory = typeStateFactory;
        this.executorFactory = executorFactory;
    }

    @Override
    public Mono<FirebaseSubscription> save(Username username, FirebaseSubscriptionCreationRequest request) {
        PostgresFirebaseSubscriptionDAO subscriptionDAO = firebaseSubscriptionDAO(username);

        return validateInputExpires(request)
            .then(Mono.defer(() -> {
                FirebaseSubscription subscription = FirebaseSubscription.from(request,
                    evaluateExpiresTime(OptionConverters.toJava(request.expires().map(FirebaseSubscriptionExpiredTime::value)), clock));
                return subscriptionDAO.save(username, subscription)
                    .thenReturn(subscription);
            }));
    }

    private Mono<Object> validateInputExpires(FirebaseSubscriptionCreationRequest request) {
        return Mono.just(request.expires())
            .handle((inputExpires, sink) -> {
                if (isInThePast(request.expires(), clock)) {
                    sink.error(new ExpireTimeInvalidException(request.expires().get().value(), "expires must be greater than now"));
                }
            });
    }

    @Override
    public Mono<FirebaseSubscriptionExpiredTime> updateExpireTime(Username username, FirebaseSubscriptionId id, ZonedDateTime newExpire) {
        return Mono.just(newExpire)
            .handle((inputTime, sink) -> {
                if (newExpire.isBefore(ZonedDateTime.now(clock))) {
                    sink.error(new ExpireTimeInvalidException(inputTime, "expires must be greater than now"));
                }
            })
            .then(firebaseSubscriptionDAO(username)
                .updateExpireTime(username, id, evaluateExpiresTime(Optional.of(newExpire), clock).value())
                .map(FirebaseSubscriptionExpiredTime::new)
                .switchIfEmpty(Mono.error(() -> new FirebaseSubscriptionNotFoundException(id))));
    }

    @Override
    public Mono<Void> updateTypes(Username username, FirebaseSubscriptionId id, Set<TypeName> types) {
        return firebaseSubscriptionDAO(username)
            .updateType(username, id, types)
            .switchIfEmpty(Mono.error(() -> new FirebaseSubscriptionNotFoundException(id)))
            .then();
    }

    @Override
    public Mono<Void> revoke(Username username, FirebaseSubscriptionId id) {
        return firebaseSubscriptionDAO(username)
            .deleteByUsernameAndId(username, id);
    }

    @Override
    public Mono<Void> revoke(Username username) {
        return firebaseSubscriptionDAO(username)
            .deleteByUsername(username);
    }

    @Override
    public Flux<FirebaseSubscription> get(Username username, Set<FirebaseSubscriptionId> ids) {
        return firebaseSubscriptionDAO(username)
            .getByUsernameAndIds(username, ids);
    }

    @Override
    public Flux<FirebaseSubscription> list(Username username) {
        return firebaseSubscriptionDAO(username)
            .listByUsername(username);
    }

    private PostgresFirebaseSubscriptionDAO firebaseSubscriptionDAO(Username username) {
        return new PostgresFirebaseSubscriptionDAO(executorFactory.create(username.getDomainPart()), typeStateFactory);
    }
}
