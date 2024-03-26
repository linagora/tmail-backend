package com.linagora.tmail.encrypted.postgres;

import static com.linagora.tmail.encrypted.postgres.table.PostgresKeystoreModule.PostgresKeystoreTable.ID;
import static com.linagora.tmail.encrypted.postgres.table.PostgresKeystoreModule.PostgresKeystoreTable.KEY;
import static com.linagora.tmail.encrypted.postgres.table.PostgresKeystoreModule.PostgresKeystoreTable.PK_CONSTRAINT_NAME;
import static com.linagora.tmail.encrypted.postgres.table.PostgresKeystoreModule.PostgresKeystoreTable.TABLE_NAME;
import static com.linagora.tmail.encrypted.postgres.table.PostgresKeystoreModule.PostgresKeystoreTable.USERNAME;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.jooq.Record;

import com.linagora.tmail.encrypted.PublicKey;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresKeystoreDAO {
    public static class Factory {
        private final PostgresExecutor.Factory executorFactory;

        @Inject
        @Singleton
        public Factory(PostgresExecutor.Factory executorFactory) {
            this.executorFactory = executorFactory;
        }

        public PostgresKeystoreDAO create(Optional<Domain> domain) {
            return new PostgresKeystoreDAO(executorFactory.create(domain));
        }
    }

    private PostgresExecutor postgresExecutor;

    public PostgresKeystoreDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<Void> insertKey(Username username, PublicKey publicKey) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
            .set(USERNAME, username.asString())
            .set(ID, publicKey.id())
            .set(KEY, publicKey.key())
            .onConflictOnConstraint(PK_CONSTRAINT_NAME)
            .doUpdate()
            .set(KEY, publicKey.key())));
    }

    public Mono<PublicKey> getKey(Username username, String keyId) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.selectFrom(TABLE_NAME)
            .where(USERNAME.eq(username.asString()))
            .and(ID.eq(keyId))))
            .map(this::toPublicKey);
    }

    public Flux<PublicKey> getAllKeys(Username username) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectFrom(TABLE_NAME)
                .where(USERNAME.eq(username.asString()))))
            .map(this::toPublicKey);
    }

    public Mono<Void> deleteKey(Username username, String keyId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(USERNAME.eq(username.asString()))
            .and(ID.eq(keyId))));
    }

    public Mono<Void> deleteAllKeys(Username username) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(USERNAME.eq(username.asString()))));
    }

    private PublicKey toPublicKey(Record record) {
        return new PublicKey(record.get(ID), record.get(KEY));
    }
}
