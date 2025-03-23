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

package com.linagora.calendar.restapi.routes.configuration;

import jakarta.inject.Inject;

import org.apache.james.mailbox.MailboxSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linagora.calendar.restapi.RestApiConfiguration;
import com.linagora.calendar.restapi.api.ConfigurationEntryResolver;
import com.linagora.calendar.restapi.api.ConfigurationKey;
import com.linagora.calendar.restapi.api.ModuleName;

import reactor.core.publisher.Mono;

public class DavConfigurationEntryResolver implements ConfigurationEntryResolver {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final RestApiConfiguration configuration;

    @Inject
    public DavConfigurationEntryResolver(RestApiConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Mono<JsonNode> resolve(MailboxSession session) {
        ObjectNode frontendNode = OBJECT_MAPPER.createObjectNode();
        frontendNode.put("url", configuration.getDavURL().toString());

        ObjectNode backendNode = OBJECT_MAPPER.createObjectNode();
        backendNode.put("url", configuration.getDavURL().toString());

        ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
        objectNode.put("frontend", frontendNode);
        objectNode.put("backend", backendNode);
        return Mono.just(objectNode);
    }

    @Override
    public ModuleName moduleName() {
        return new ModuleName("core");
    }

    @Override
    public ConfigurationKey configurationKey() {
        return new ConfigurationKey("davserver");
    }
}
