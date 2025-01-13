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

package com.linagora.tmail.contact;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public record UserSearchResponse(@JsonProperty("_id") String id,
                                 @JsonProperty("preferredEmail") String preferredEmail,
                                 @JsonProperty("emails") List<String> emails) {

    public static class Deserializer {
        private final ObjectMapper objectMapper;

        public Deserializer() {
            this.objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }

        public List<UserSearchResponse> deserialize(byte[] jsonBytes) throws IOException {
            return objectMapper.readValue(jsonBytes, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, UserSearchResponse.class));
        }
    }
}