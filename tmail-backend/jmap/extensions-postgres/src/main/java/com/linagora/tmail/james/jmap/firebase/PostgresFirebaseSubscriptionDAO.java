package com.linagora.tmail.james.jmap.firebase;

import static com.linagora.tmail.james.jmap.firebase.PostgresFirebaseModule.FirebaseSubscriptionTable.DEVICE_CLIENT_ID;
import static com.linagora.tmail.james.jmap.firebase.PostgresFirebaseModule.FirebaseSubscriptionTable.EXPIRES;
import static com.linagora.tmail.james.jmap.firebase.PostgresFirebaseModule.FirebaseSubscriptionTable.FCM_TOKEN;
import static com.linagora.tmail.james.jmap.firebase.PostgresFirebaseModule.FirebaseSubscriptionTable.FCM_TOKEN_UNIQUE_CONSTRAINT;
import static com.linagora.tmail.james.jmap.firebase.PostgresFirebaseModule.FirebaseSubscriptionTable.ID;
import static com.linagora.tmail.james.jmap.firebase.PostgresFirebaseModule.FirebaseSubscriptionTable.PRIMARY_KEY_CONSTRAINT;
import static com.linagora.tmail.james.jmap.firebase.PostgresFirebaseModule.FirebaseSubscriptionTable.TABLE_NAME;
import static com.linagora.tmail.james.jmap.firebase.PostgresFirebaseModule.FirebaseSubscriptionTable.TYPES;
import static com.linagora.tmail.james.jmap.firebase.PostgresFirebaseModule.FirebaseSubscriptionTable.USER;
import static org.apache.james.backends.postgres.PostgresCommons.IN_CLAUSE_MAX_SIZE;
import static org.apache.james.backends.postgres.utils.PostgresUtils.UNIQUE_CONSTRAINT_VIOLATION_PREDICATE;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.change.TypeStateFactory;
import org.apache.james.jmap.api.model.TypeName;
import org.jooq.Record;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.linagora.tmail.james.jmap.model.DeviceClientIdInvalidException;
import com.linagora.tmail.james.jmap.model.FirebaseSubscription;
import com.linagora.tmail.james.jmap.model.FirebaseSubscriptionExpiredTime;
import com.linagora.tmail.james.jmap.model.FirebaseSubscriptionId;
import com.linagora.tmail.james.jmap.model.TokenInvalidException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;

public class PostgresFirebaseSubscriptionDAO {
    private static final Function<OffsetDateTime, ZonedDateTime> OFFSET_DATE_TIME_ZONED_DATE_TIME_FUNCTION = offsetDateTime ->
        Optional.ofNullable(offsetDateTime)
            .map(value -> value.atZoneSameInstant(ZoneId.of("UTC")))
            .orElse(null);

    private static final Predicate<Throwable> IS_PRIMARY_KEY_UNIQUE_CONSTRAINT = throwable -> throwable.getMessage().contains(PRIMARY_KEY_CONSTRAINT);
    private static final Predicate<Throwable> IS_FCM_TOKEN_UNIQUE_CONSTRAINT = throwable -> throwable.getMessage().contains(FCM_TOKEN_UNIQUE_CONSTRAINT);

    private final PostgresExecutor postgresExecutor;
    private final TypeStateFactory typeStateFactory;

    public PostgresFirebaseSubscriptionDAO(PostgresExecutor postgresExecutor, TypeStateFactory typeStateFactory) {
        this.postgresExecutor = postgresExecutor;
        this.typeStateFactory = typeStateFactory;
    }

    public Mono<Void> save(Username username, FirebaseSubscription subscription) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
                .set(USER, username.asString())
                .set(DEVICE_CLIENT_ID, subscription.deviceClientId())
                .set(ID, subscription.id().value())
                .set(EXPIRES, subscription.expires().value().toOffsetDateTime())
                .set(TYPES, CollectionConverters.asJava(subscription.types())
                    .stream().map(TypeName::asString).toArray(String[]::new))
                .set(FCM_TOKEN, subscription.token())))
            .onErrorMap(UNIQUE_CONSTRAINT_VIOLATION_PREDICATE.and(IS_PRIMARY_KEY_UNIQUE_CONSTRAINT),
                e -> new DeviceClientIdInvalidException(subscription.deviceClientId(), "deviceClientId must be unique"))
            .onErrorMap(UNIQUE_CONSTRAINT_VIOLATION_PREDICATE.and(IS_FCM_TOKEN_UNIQUE_CONSTRAINT),
                e -> new TokenInvalidException("deviceToken must be unique"));
    }

    public Flux<FirebaseSubscription> listByUsername(Username username) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(DEVICE_CLIENT_ID, ID, EXPIRES, TYPES, FCM_TOKEN)
                .from(TABLE_NAME)
                .where(USER.eq(username.asString()))))
            .map(this::toSubscription);
    }

    public Flux<FirebaseSubscription> getByUsernameAndIds(Username username, Collection<FirebaseSubscriptionId> ids) {
        Function<Collection<FirebaseSubscriptionId>, Flux<FirebaseSubscription>> queryPublisherFunction = idsMatching -> postgresExecutor.executeRows(dslContext ->
                Flux.from(dslContext.select(DEVICE_CLIENT_ID, ID, EXPIRES, TYPES, FCM_TOKEN)
                    .from(TABLE_NAME)
                    .where(USER.eq(username.asString()))
                    .and(ID.in(idsMatching.stream().map(FirebaseSubscriptionId::value)
                        .toList()))))
            .map(this::toSubscription);

        if (ids.size() <= IN_CLAUSE_MAX_SIZE) {
            return queryPublisherFunction.apply(ids);
        } else {
            return Flux.fromIterable(Iterables.partition(ids, IN_CLAUSE_MAX_SIZE))
                .flatMap(queryPublisherFunction);
        }
    }

    public Mono<Void> deleteByUsername(Username username) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(USER.eq(username.asString()))));
    }

    public Mono<Void> deleteByUsernameAndId(Username username, FirebaseSubscriptionId id) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(USER.eq(username.asString()))
            .and(ID.eq(id.value()))));
    }

    public Mono<Set<TypeName>> updateType(Username username, FirebaseSubscriptionId id, Set<TypeName> newTypes) {
        Preconditions.checkNotNull(newTypes, "newTypes should not be null");
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.update(TABLE_NAME)
                .set(TYPES, newTypes.stream().map(TypeName::asString).toArray(String[]::new))
                .where(USER.eq(username.asString()))
                .and(ID.eq(id.value()))
                .returning(TYPES)))
            .map(this::extractTypes);
    }

    public Mono<ZonedDateTime> updateExpireTime(Username username, FirebaseSubscriptionId id, ZonedDateTime newExpire) {
        Preconditions.checkNotNull(newExpire, "newExpire should not be null");
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.update(TABLE_NAME)
                .set(EXPIRES, newExpire.toOffsetDateTime())
                .where(USER.eq(username.asString()))
                .and(ID.eq(id.value()))
                .returning(EXPIRES)))
            .map(record -> OFFSET_DATE_TIME_ZONED_DATE_TIME_FUNCTION.apply(record.get(EXPIRES)));
    }

    private FirebaseSubscription toSubscription(Record record) {
        return new FirebaseSubscription(new FirebaseSubscriptionId(record.get(ID)),
            record.get(DEVICE_CLIENT_ID),
            record.get(FCM_TOKEN),
            toExpires(record),
            CollectionConverters.asScala(extractTypes(record)).toSeq());
    }

    private FirebaseSubscriptionExpiredTime toExpires(Record record) {
        return new FirebaseSubscriptionExpiredTime(OFFSET_DATE_TIME_ZONED_DATE_TIME_FUNCTION.apply(record.get(EXPIRES)));
    }

    private Set<TypeName> extractTypes(Record record) {
        return Arrays.stream(record.get(TYPES))
            .map(string -> typeStateFactory.parse(string).right().get())
            .collect(Collectors.toSet());
    }
}