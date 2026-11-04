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

package com.linagora.tmail.dav.cal;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.dav.DavUid;
import com.linagora.tmail.dav.OpenPaaSUserId;

public class FreeBusySerializer {
    public static final FreeBusySerializer INSTANCE = new FreeBusySerializer();

    private final ObjectMapper objectMapper;

    public FreeBusySerializer() {
        this.objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        SimpleModule module = new SimpleModule();
        module.addSerializer(Instant.class, new InstantSerializer());
        module.addDeserializer(Instant.class, new InstantDeserializer());
        module.addSerializer(OpenPaaSUserId.class, new OpenPaaSUserIdSerializer());
        module.addSerializer(DavUid.class, new DavUidSerializer());
        this.objectMapper.registerModule(module);
    }

    public String serialize(FreeBusyRequest request) {
        return Throwing.supplier(() -> objectMapper.writeValueAsString(request))
            .get();
    }

    public byte[] serializeAsBytes(FreeBusyRequest request) {
        return Throwing.supplier(() -> objectMapper.writeValueAsBytes(request))
            .get();
    }

    public FreeBusyResponse deserialize(String json) {
        return requireResponse(Throwing.supplier(() -> objectMapper.readValue(json, FreeBusyResponse.class)).get());
    }

    public FreeBusyResponse deserialize(byte[] json) {
        return requireResponse(Throwing.supplier(() -> objectMapper.readValue(json, FreeBusyResponse.class)).get());
    }

    public Optional<FreeBusyResponse> deserializeIfPresent(byte[] json) {
        JsonNode root = Throwing.supplier(() -> objectMapper.readTree(json)).get();
        return Optional.ofNullable(root)
            .filter(node -> !node.isNull() && !node.isMissingNode())
            .map(Throwing.function(node -> objectMapper.treeToValue(node, FreeBusyResponse.class)));
    }

    private FreeBusyResponse requireResponse(FreeBusyResponse response) {
        return Optional.ofNullable(response)
            .orElseThrow(() -> new IllegalArgumentException("Free/busy response must not be null"));
    }

    public static class InstantSerializer extends JsonSerializer<Instant> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX")
            .withZone(ZoneOffset.UTC);

        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(FORMATTER.format(value));
        }
    }

    public static class InstantDeserializer extends JsonDeserializer<Instant> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX");

        @Override
        public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return Instant.from(FORMATTER.parse(p.getText()));
        }
    }

    public static class OpenPaaSUserIdSerializer extends JsonSerializer<OpenPaaSUserId> {
        @Override
        public void serialize(OpenPaaSUserId value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.value());
        }
    }

    public static class DavUidSerializer extends JsonSerializer<DavUid> {
        @Override
        public void serialize(DavUid value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.value());
        }
    }
}
