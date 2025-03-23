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

package com.linagora.calendar.restapi.api;

import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Table;

public record ConfigurationDocument(Table<ModuleName, ConfigurationKey, JsonNode> table) {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public JsonNode asJson() {
        return OBJECT_MAPPER.createArrayNode()
            .addAll(table.rowMap()
                .entrySet()
                .stream()
                .map(entry -> {
                    ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
                    objectNode.put("name", OBJECT_MAPPER.getNodeFactory().textNode(entry.getKey().name()));
                    objectNode.put("configurations", entryAsJson(entry.getValue()));
                    return objectNode;
                })
                .collect(Collectors.toList()));
    }

    private JsonNode entryAsJson(Map<ConfigurationKey, JsonNode> entry) {
        return OBJECT_MAPPER.createArrayNode()
            .addAll(entry.entrySet().stream()
                .map(confEntry -> {
                    ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
                    objectNode.put("name", OBJECT_MAPPER.getNodeFactory().textNode(confEntry.getKey().value()));
                    objectNode.put("value", confEntry.getValue());
                    return objectNode;
                })
                .collect(Collectors.toList()));
    }
}
