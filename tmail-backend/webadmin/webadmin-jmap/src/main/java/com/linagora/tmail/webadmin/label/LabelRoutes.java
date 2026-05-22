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

package com.linagora.tmail.webadmin.label;

import static org.apache.james.webadmin.Constants.SEPARATOR;
import static spark.Spark.halt;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.mail.Keyword;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.linagora.tmail.james.jmap.label.LabelRepository;
import com.linagora.tmail.james.jmap.model.DescriptionUpdate;
import com.linagora.tmail.james.jmap.model.Label;
import com.linagora.tmail.james.jmap.model.LabelId;
import com.linagora.tmail.james.jmap.model.LabelNotFoundException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.compat.java8.OptionConverters;
import spark.Request;
import spark.Service;

public class LabelRoutes implements Routes {
    static final String USERS_BASE = SEPARATOR + "users";
    private static final String USERNAME_PARAM = ":username";
    private static final String LABEL_ID_PARAM = ":labelId";
    static final String LABELS_PATH = USERS_BASE + SEPARATOR + USERNAME_PARAM + SEPARATOR + "labels";
    static final String SPECIFIC_LABEL_PATH = LABELS_PATH + SEPARATOR + LABEL_ID_PARAM;

    private final LabelRepository labelRepository;
    private final UsersRepository usersRepository;
    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<CreateLabelRequest> createExtractor;
    private final JsonExtractor<UpdateLabelRequest> updateExtractor;

    @Inject
    public LabelRoutes(LabelRepository labelRepository, UsersRepository usersRepository,
                       JsonTransformer jsonTransformer) {
        this.labelRepository = labelRepository;
        this.usersRepository = usersRepository;
        this.jsonTransformer = jsonTransformer;
        this.createExtractor = new JsonExtractor<>(CreateLabelRequest.class);
        this.updateExtractor = new JsonExtractor<>(UpdateLabelRequest.class);
    }

    @Override
    public String getBasePath() {
        return USERS_BASE;
    }

    @Override
    public void define(Service service) {
        service.get(LABELS_PATH, (req, res) -> listLabels(req), jsonTransformer);
        service.post(LABELS_PATH, this::createLabel, jsonTransformer);
        service.patch(SPECIFIC_LABEL_PATH, (req, res) -> {
            updateLabel(req);
            return halt(HttpStatus.NO_CONTENT_204);
        });
        service.delete(SPECIFIC_LABEL_PATH, (req, res) -> {
            deleteLabel(req);
            return halt(HttpStatus.NO_CONTENT_204);
        });
    }

    private Object listLabels(Request request) throws UsersRepositoryException {
        Username username = extractUsername(request);
        assertUserExists(username);

        return Flux.from(labelRepository.listLabels(username))
            .map(LabelDTO::from)
            .collectList()
            .block();
    }

    private Object createLabel(Request request, spark.Response response) throws UsersRepositoryException, JsonExtractException {
        Username username = extractUsername(request);
        assertUserExists(username);
        CreateLabelRequest body = createExtractor.parse(request.body());

        Label created = body.validatedKeyword()
            .map(keyword -> {
                Label label = body.asLabel();
                Mono.from(labelRepository.addLabel(username, label)).block();
                return label;
            })
            .orElseGet(() -> Mono.from(labelRepository.addLabel(username, body.toCreationRequest())).block());

        response.status(HttpStatus.CREATED_201);
        return LabelDTO.from(created);
    }

    private void updateLabel(Request request) throws UsersRepositoryException, JsonExtractException {
        Username username = extractUsername(request);
        assertUserExists(username);
        LabelId labelId = extractLabelId(request);
        UpdateLabelRequest body = updateExtractor.parse(request.body());

        try {
            Mono.from(labelRepository.updateLabel(
                    username, labelId,
                    OptionConverters.toScala(Optional.of(body.displayName())),
                    OptionConverters.toScala(body.color()),
                    OptionConverters.toScala(Optional.of(new DescriptionUpdate(OptionConverters.toScala(body.description()))))))
                .block();
            body.readOnly().ifPresent(readOnly ->
                Mono.from(labelRepository.setLabelReadOnly(username, labelId, readOnly))
                    .block());
        } catch (LabelNotFoundException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .message("Label '%s' not found for user '%s'", labelId.serialize(), username.asString())
                .haltError();
        }
    }

    private void deleteLabel(Request request) throws UsersRepositoryException {
        Username username = extractUsername(request);
        assertUserExists(username);
        LabelId labelId = extractLabelId(request);

        Mono.from(labelRepository.deleteLabel(username, labelId)).block();
    }

    private Username extractUsername(Request request) {
        return Username.of(request.params(USERNAME_PARAM));
    }

    private LabelId extractLabelId(Request request) {
        String rawId = request.params(LABEL_ID_PARAM);
        if (!Keyword.of(rawId).isSuccess()) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid label id '%s'", rawId)
                .haltError();
        }
        return LabelId.fromKeyword(rawId);
    }

    private void assertUserExists(Username username) throws UsersRepositoryException {
        if (!usersRepository.contains(username)) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .message("User '%s' does not exist", username.asString())
                .haltError();
        }
    }
}
