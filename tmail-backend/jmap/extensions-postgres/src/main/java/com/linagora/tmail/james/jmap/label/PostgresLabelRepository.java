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

import jakarta.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.reactivestreams.Publisher;

import com.linagora.tmail.james.jmap.model.Color;
import com.linagora.tmail.james.jmap.model.DisplayName;
import com.linagora.tmail.james.jmap.model.Label;
import com.linagora.tmail.james.jmap.model.LabelCreationRequest;
import com.linagora.tmail.james.jmap.model.LabelId;

import reactor.core.publisher.Flux;
import scala.Option;

public class PostgresLabelRepository implements LabelRepository {
    private final PostgresExecutor.Factory executorFactory;

    @Inject
    public PostgresLabelRepository(PostgresExecutor.Factory executorFactory) {
        this.executorFactory = executorFactory;
    }

    @Override
    public Publisher<Label> addLabel(Username username, LabelCreationRequest labelCreationRequest) {
        return labelDAO(username)
            .insert(username, labelCreationRequest.toLabel());
    }

    @Override
    public Publisher<Void> addLabel(Username username, Label label) {
        return labelDAO(username)
            .insert(username, label)
            .then();
    }

    @Override
    public Publisher<Label> addLabels(Username username, Collection<LabelCreationRequest> labelCreationRequests) {
        PostgresLabelDAO labelDAO = labelDAO(username);

        return Flux.fromIterable(labelCreationRequests)
            .concatMap(labelCreationRequest -> labelDAO.insert(username, labelCreationRequest.toLabel()));
    }

    @Override
    public Publisher<Void> updateLabel(Username username, LabelId labelId, Option<DisplayName> newDisplayName, Option<Color> newColor, Option<String> newDescription) {
        return labelDAO(username)
            .updateLabel(username, labelId, newDisplayName, newColor, newDescription);
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
            .deleteOne(username, labelId.toKeyword());
    }

    @Override
    public Publisher<Void> deleteAllLabels(Username username) {
        return labelDAO(username)
            .deleteAll(username);
    }

    private PostgresLabelDAO labelDAO(Username username) {
        return new PostgresLabelDAO(executorFactory.create(username.getDomainPart()));
    }
}
