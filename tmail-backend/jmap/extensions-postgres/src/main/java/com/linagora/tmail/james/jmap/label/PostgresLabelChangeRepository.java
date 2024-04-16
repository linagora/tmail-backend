package com.linagora.tmail.james.jmap.label;

import java.time.Clock;

import jakarta.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.change.Limit;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.api.exception.ChangeNotFoundException;
import org.apache.james.jmap.api.model.AccountId;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.Option;
import scala.collection.immutable.Set$;
import scala.jdk.javaapi.OptionConverters;

public class PostgresLabelChangeRepository implements LabelChangeRepository {
    private final PostgresExecutor.Factory executorFactory;
    private final Clock clock;

    @Inject
    public PostgresLabelChangeRepository(PostgresExecutor.Factory executorFactory, Clock clock) {
        this.executorFactory = executorFactory;
        this.clock = clock;
    }

    @Override
    public Mono<Void> save(LabelChange change) {
        return labelChangeDAO(change.getAccountId())
            .insert(change);
    }

    @Override
    public Mono<LabelChanges> getSinceState(AccountId accountId, State state, Option<Limit> maxChanges) {
        Preconditions.checkNotNull(accountId);
        Preconditions.checkNotNull(state);

        int maxIds = OptionConverters.toJava(maxChanges)
            .orElse(LabelChangeRepository.DEFAULT_MAX_IDS_TO_RETURN())
            .getValue();
        Preconditions.checkArgument(maxIds > 0, "maxChanges must be a positive integer");

        PostgresLabelChangeDAO labelChangeDAO = labelChangeDAO(accountId);

        if (state.equals(State.INITIAL)) {
            return getAllChanges(accountId, maxIds, labelChangeDAO);
        }

        return getChangesSince(accountId, state, maxIds, labelChangeDAO);
    }

    private Mono<LabelChanges> getChangesSince(AccountId accountId, State state, int maxIds, PostgresLabelChangeDAO labelChangeDAO) {
        return labelChangeDAO.getChangesSince(accountId, state)
            .switchIfEmpty(Flux.error(() -> new ChangeNotFoundException(state, String.format("State '%s' could not be found", state.getValue()))))
            .filter(change -> !change.state().equals(state))
            .switchIfEmpty(fallbackToTheCurrentState(accountId, state))
            .map(LabelChanges::from)
            .reduce(LabelChanges.initial(), (change1, change2) -> LabelChanges.merge(maxIds, change1, change2));
    }

    private Mono<LabelChange> fallbackToTheCurrentState(AccountId accountId, State state) {
        return Mono.defer(() -> Mono.just(LabelChange.apply(
            accountId, Set$.MODULE$.empty(), Set$.MODULE$.empty(), Set$.MODULE$.empty(), state)));
    }

    private Mono<LabelChanges> getAllChanges(AccountId accountId, int maxIds, PostgresLabelChangeDAO labelChangeDAO) {
        return labelChangeDAO.getAllChanges(accountId)
            .map(LabelChanges::from)
            .reduce(LabelChanges.initial(), (change1, change2) -> LabelChanges.merge(maxIds, change1, change2));
    }

    @Override
    public Mono<State> getLatestState(AccountId accountId) {
        return labelChangeDAO(accountId).getLatestState(accountId)
            .switchIfEmpty(Mono.just(State.INITIAL));
    }

    private PostgresLabelChangeDAO labelChangeDAO(AccountId accountId) {
        return new PostgresLabelChangeDAO(executorFactory.create(Username.of(accountId.getIdentifier()).getDomainPart()),
            clock);
    }
}
