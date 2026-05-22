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

package com.linagora.tmail.webadmin.jmap;

import static org.apache.james.webadmin.Constants.SEPARATOR;
import static spark.Spark.halt;

import java.util.Map;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.linagora.tmail.james.jmap.settings.JmapSettingsKey;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsUpsertRequest;
import com.linagora.tmail.james.jmap.settings.JmapSettingsValue;

import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;
import spark.Request;
import spark.Service;

public class JmapSettingsRoutes implements Routes {
    static final String USERS_BASE = SEPARATOR + "users";
    private static final String USERNAME_PARAM = ":username";
    static final String JMAP_SETTINGS_PATH = USERS_BASE + SEPARATOR + USERNAME_PARAM + SEPARATOR + "jmap" + SEPARATOR + "settings";

    static class SettingsMap {
        private final java.util.LinkedHashMap<String, String> entries = new java.util.LinkedHashMap<>();

        @JsonAnySetter
        public void set(String key, String value) {
            entries.put(key, value);
        }

        @JsonAnyGetter
        public Map<String, String> entries() {
            return entries;
        }
    }

    private final JmapSettingsRepository jmapSettingsRepository;
    private final UsersRepository usersRepository;
    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<SettingsMap> bodyExtractor;

    @Inject
    public JmapSettingsRoutes(JmapSettingsRepository jmapSettingsRepository,
                              UsersRepository usersRepository,
                              JsonTransformer jsonTransformer) {
        this.jmapSettingsRepository = jmapSettingsRepository;
        this.usersRepository = usersRepository;
        this.jsonTransformer = jsonTransformer;
        this.bodyExtractor = new JsonExtractor<>(SettingsMap.class);
    }

    @Override
    public String getBasePath() {
        return USERS_BASE;
    }

    @Override
    public void define(Service service) {
        service.get(JMAP_SETTINGS_PATH, (req, res) -> getSettings(req), jsonTransformer);
        service.put(JMAP_SETTINGS_PATH, (req, res) -> {
            putSettings(req);
            return halt(HttpStatus.NO_CONTENT_204);
        });
    }

    private Map<String, String> getSettings(Request request) throws UsersRepositoryException {
        Username username = extractUsername(request);
        assertUserExists(username);

        return Mono.from(jmapSettingsRepository.get(username))
            .map(settings -> CollectionConverters.<JmapSettingsKey, JmapSettingsValue>asJava(settings.settings())
                .entrySet().stream()
                .collect(Collectors.toMap(
                    e -> e.getKey().asString(),
                    e -> e.getValue().value())))
            .defaultIfEmpty(Map.of())
            .block();
    }

    private void putSettings(Request request) throws UsersRepositoryException, JsonExtractException {
        Username username = extractUsername(request);
        assertUserExists(username);

        SettingsMap body = bodyExtractor.parse(request.body());
        JmapSettingsUpsertRequest upsertRequest = toUpsertRequest(body.entries());
        Mono.from(jmapSettingsRepository.reset(username, upsertRequest)).block();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private JmapSettingsUpsertRequest toUpsertRequest(Map<String, String> settings) {
        try {
            scala.collection.immutable.Map scalaMap = scala.collection.immutable.Map$.MODULE$.empty();
            for (Map.Entry<String, String> entry : settings.entrySet()) {
                scalaMap = (scala.collection.immutable.Map) scalaMap.updated(
                    JmapSettingsKey.liftOrThrow(entry.getKey()),
                    new JmapSettingsValue(entry.getValue()));
            }
            return new JmapSettingsUpsertRequest(scalaMap);
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(e.getMessage())
                .haltError();
        }
    }

    private Username extractUsername(Request request) {
        return Username.of(request.params(USERNAME_PARAM));
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
