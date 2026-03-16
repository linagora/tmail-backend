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

package com.linagora.tmail.james.jmap.label;

import java.util.Collection;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.jmap.change.AccountIdRegistrationKey;
import org.reactivestreams.Publisher;

import com.linagora.tmail.james.jmap.model.Color;
import com.linagora.tmail.james.jmap.model.DescriptionUpdate;
import com.linagora.tmail.james.jmap.model.DisplayName;
import com.linagora.tmail.james.jmap.model.Label;
import com.linagora.tmail.james.jmap.model.LabelCreationRequest;
import com.linagora.tmail.james.jmap.model.LabelId;
import com.linagora.tmail.james.jmap.model.LabelNotFoundException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.Option;
import scala.jdk.javaapi.OptionConverters;

public class PostgresLabelRepository implements LabelRepository {
    private final PostgresExecutor.Factory executorFactory;
    private final EventBus eventBus;

    @Inject
    public PostgresLabelRepository(PostgresExecutor.Factory executorFactory,
                                   @Named("TMAIL_EVENT_BUS") EventBus eventBus) {
        this.executorFactory = executorFactory;
        this.eventBus = eventBus;
    }

    @Override
    public Publisher<Label> addLabel(Username username, LabelCreationRequest labelCreationRequest) {
        return labelDAO(username)
            .insert(username, labelCreationRequest.toLabel())
            .flatMap(label -> Mono.from(eventBus.dispatch(
                    new LabelCreated(Event.EventId.random(), username, label),
                    new AccountIdRegistrationKey(AccountId.fromUsername(username))))
                .thenReturn(label));
    }

    @Override
    public Publisher<Void> addLabel(Username username, Label label) {
        return labelDAO(username)
            .insert(username, label)
            .then(Mono.from(eventBus.dispatch(
                new LabelCreated(Event.EventId.random(), username, label),
                new AccountIdRegistrationKey(AccountId.fromUsername(username)))))
            .then();
    }

    @Override
    public Publisher<Label> addLabels(Username username, Collection<LabelCreationRequest> labelCreationRequests) {

        return Flux.fromIterable(labelCreationRequests)
            .concatMap(labelCreationRequest -> Mono.from(addLabel(username, labelCreationRequest)));
    }

    @Override
    public Publisher<Void> updateLabel(Username username, LabelId labelId, Option<DisplayName> newDisplayName, Option<Color> newColor, Option<DescriptionUpdate> newDescription) {
        PostgresLabelDAO dao = labelDAO(username);

        return Mono.from(dao.selectSome(username, List.of(labelId.toKeyword())))
            .switchIfEmpty(Mono.error(new LabelNotFoundException(labelId)))
            .flatMap(oldLabel -> dao.updateLabel(username, labelId, newDisplayName, newColor, newDescription)
                .then(Mono.from(eventBus.dispatch(
                    new LabelUpdated(Event.EventId.random(), username,
                        computeUpdatedLabel(oldLabel, newDisplayName, newColor, newDescription)),
                    new AccountIdRegistrationKey(AccountId.fromUsername(username))))))
            .then();
    }

    @Override
    public Publisher<Label> getLabels(Username username, Collection<LabelId> ids) {
        return labelDAO(username)
            .selectSome(username, ids.stream()
                .map(LabelId::toKeyword)
                .toList());
    }

    @Override
    public Publisher<Label> listLabels(Username username) {
        return labelDAO(username)
            .selectAll(username);
    }

    @Override
    public Publisher<Void> deleteLabel(Username username, LabelId labelId) {
        return labelDAO(username)
            .deleteOne(username, labelId.toKeyword())
            .then(Mono.from(eventBus.dispatch(
                new LabelDestroyed(Event.EventId.random(), username, labelId),
                new AccountIdRegistrationKey(AccountId.fromUsername(username)))))
            .then();
    }

    @Override
    public Publisher<Void> deleteAllLabels(Username username) {
        return Flux.from(listLabels(username))
            .map(label -> LabelId.fromKeyword(label.keyword()))
            .concatMap(labelId -> Mono.from(deleteLabel(username, labelId)))
            .then();
    }

    @Override
    public Publisher<Void> setLabelReadOnly(Username username, LabelId labelId, boolean readOnly) {
        PostgresLabelDAO dao = labelDAO(username);
        return Mono.from(dao.selectSome(username, List.of(labelId.toKeyword())))
            .switchIfEmpty(Mono.error(new LabelNotFoundException(labelId)))
            .flatMap(label -> dao.setReadOnly(username, labelId, readOnly)
                .then(Mono.from(eventBus.dispatch(
                    new LabelUpdated(Event.EventId.random(), username,
                        new Label(label.id(), label.displayName(), label.keyword(), label.color(), label.description(), readOnly)),
                    new AccountIdRegistrationKey(AccountId.fromUsername(username))))))
            .then();
    }

    private PostgresLabelDAO labelDAO(Username username) {
        return new PostgresLabelDAO(executorFactory.create(username.getDomainPart()));
    }

    private Label computeUpdatedLabel(Label oldLabel, Option<DisplayName> newDisplayName, Option<Color> newColor, Option<DescriptionUpdate> newDescription) {
        DisplayName displayName = OptionConverters.toJava(newDisplayName).orElse(oldLabel.displayName());
        Option<Color> color = newColor.isDefined() ? newColor : oldLabel.color();
        Option<String> description = OptionConverters.toJava(newDescription)
            .<Option<String>>map(du -> OptionConverters.toJava(du.value())
                .<Option<String>>map(d -> scala.Option.apply(d))
                .orElse(scala.Option.empty()))
            .orElse(oldLabel.description());
        return new Label(oldLabel.id(), displayName, oldLabel.keyword(), color, description, oldLabel.readOnly());
    }
}
