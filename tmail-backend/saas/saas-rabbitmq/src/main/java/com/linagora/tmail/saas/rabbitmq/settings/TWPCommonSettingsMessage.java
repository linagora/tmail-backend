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

package com.linagora.tmail.saas.rabbitmq.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.base.Preconditions;

public record TWPCommonSettingsMessage(String source, String nickname, String requestId,
                                       Long timestamp, Long version, Payload payload) {
    public static class TWPSettingsMessageParseException extends RuntimeException {
        TWPSettingsMessageParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final Deserializer DESERIALIZER = new Deserializer();

    public static class Deserializer {
        public static TWPCommonSettingsMessage parseAMQPMessage(String messagePayload) {
            return parseAMQPMessage(messagePayload.getBytes(StandardCharsets.UTF_8));
        }

        public static TWPCommonSettingsMessage parseAMQPMessage(byte[] messagePayload) {
            try {
                return DESERIALIZER.deserialize(messagePayload);
            } catch (Exception e) {
                throw new TWPSettingsMessageParseException("Failed to parse TWP settings message: " + new String(messagePayload, StandardCharsets.UTF_8), e);
            }
        }

        private final ObjectMapper objectMapper;

        public Deserializer() {
            this.objectMapper = new ObjectMapper()
                .registerModule(new Jdk8Module())
                .registerModule(new GuavaModule());
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }

        public TWPCommonSettingsMessage deserialize(byte[] jsonBytes) throws IOException {
            return objectMapper.readValue(jsonBytes, TWPCommonSettingsMessage.class);
        }
    }

    public record Payload(String email,
                          Optional<String> language) {
        @JsonCreator
        public Payload(@JsonProperty("email") String email,
                       @JsonProperty("language") Optional<String> language) {
            Preconditions.checkNotNull(email, "email cannot be null");

            this.email = email;
            this.language = language;
        }
    }

    @JsonCreator
    public TWPCommonSettingsMessage(@JsonProperty("source") String source,
                                    @JsonProperty("nickname") String nickname,
                                    @JsonProperty("request_id") String requestId,
                                    @JsonProperty("timestamp") Long timestamp,
                                    @JsonProperty("version") Long version,
                                    @JsonProperty("payload") Payload payload) {
        Preconditions.checkNotNull(source, "source cannot be null");
        Preconditions.checkNotNull(nickname, "nickname cannot be null");
        Preconditions.checkNotNull(requestId, "request_id cannot be null");
        Preconditions.checkNotNull(timestamp, "timestamp cannot be null");
        Preconditions.checkNotNull(version, "version cannot be null");
        Preconditions.checkNotNull(payload, "payload cannot be null");

        this.source = source;
        this.nickname = nickname;
        this.requestId = requestId;
        this.timestamp = timestamp;
        this.version = version;
        this.payload = payload;
    }
}