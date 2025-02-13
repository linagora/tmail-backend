package com.linagora.tmail.james.jmap.label;

import static com.linagora.tmail.james.jmap.label.PostgresLabelModule.LabelChangeTable.ACCOUNT_ID;
import static com.linagora.tmail.james.jmap.label.PostgresLabelModule.LabelChangeTable.CREATED;
import static com.linagora.tmail.james.jmap.label.PostgresLabelModule.LabelChangeTable.CREATED_DATE;
import static com.linagora.tmail.james.jmap.label.PostgresLabelModule.LabelChangeTable.DESTROYED;
import static com.linagora.tmail.james.jmap.label.PostgresLabelModule.LabelChangeTable.STATE;
import static com.linagora.tmail.james.jmap.label.PostgresLabelModule.LabelChangeTable.TABLE_NAME;
import static com.linagora.tmail.james.jmap.label.PostgresLabelModule.LabelChangeTable.UPDATED;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Arrays;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.api.model.AccountId;
import org.jooq.Record;

import com.linagora.tmail.james.jmap.model.LabelChange;
import com.linagora.tmail.james.jmap.model.LabelId;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.collection.immutable.Set;
import scala.jdk.javaapi.CollectionConverters;

public class PostgresLabelChangeDAO {
    private final PostgresExecutor postgresExecutor;
    private final Clock clock;

    public PostgresLabelChangeDAO(PostgresExecutor postgresExecutor,
                                  Clock clock) {
        this.postgresExecutor = postgresExecutor;
        this.clock = clock;
    }

    public Mono<Void> insert(LabelChange change) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
            .set(ACCOUNT_ID, change.getAccountId().getIdentifier())
            .set(STATE, change.state().getValue())
            .set(CREATED, toStringArray(change.created()))
            .set(UPDATED, toStringArray(change.updated()))
            .set(DESTROYED, toStringArray(change.destroyed()))
            .set(CREATED_DATE, ZonedDateTime.now(clock).toOffsetDateTime())));
    }

    public Flux<LabelChange> getAllChanges(AccountId accountId) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(STATE, CREATED, UPDATED, DESTROYED)
                .from(TABLE_NAME)
                .where(ACCOUNT_ID.eq(accountId.getIdentifier()))))
            .map(record -> toLabelChange(record, accountId));
    }

    public Flux<LabelChange> getChangesSince(AccountId accountId, State state) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(STATE, CREATED, UPDATED, DESTROYED)
                .from(TABLE_NAME)
                .where(ACCOUNT_ID.eq(accountId.getIdentifier()))
                .and(STATE.greaterOrEqual(state.getValue()))
                .orderBy(STATE)))
            .map(record -> toLabelChange(record, accountId));
    }

    public Mono<State> getLatestState(AccountId accountId) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(STATE)
                .from(TABLE_NAME)
                .where(ACCOUNT_ID.eq(accountId.getIdentifier()))
                .orderBy(STATE.desc())
                .limit(1)))
            .map(record -> State.of(record.get(STATE)));
    }

    private String[] toStringArray(Set<LabelId> labelIds) {
        return CollectionConverters.asJava(labelIds)
            .stream()
            .map(LabelId::serialize)
            .toArray(String[]::new);
    }

    private LabelChange toLabelChange(Record record, AccountId accountId) {
        return LabelChange.apply(accountId,
            toLabelIdSet(record.get(CREATED)),
            toLabelIdSet(record.get(UPDATED)),
            toLabelIdSet(record.get(DESTROYED)),
            State.of(record.get(STATE)));
    }

    private Set<LabelId> toLabelIdSet(String[] strings) {
        return CollectionConverters.asScala(Arrays.stream(strings)
                .map(LabelId::fromKeyword)
                .iterator())
            .toSet();
    }
}
