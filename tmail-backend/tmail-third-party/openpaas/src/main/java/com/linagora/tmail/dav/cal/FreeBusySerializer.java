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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.fge.lambdas.Throwing;

public class FreeBusySerializer {
    public static final FreeBusySerializer INSTANCE = new FreeBusySerializer();

    private final ObjectMapper objectMapper;

    public FreeBusySerializer() {
        this.objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Instant.class, new InstantSerializer());
        module.addDeserializer(Instant.class, new InstantDeserializer());
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
        return Throwing.supplier(() -> objectMapper.readValue(json, FreeBusyResponse.class)).get();
    }

    public FreeBusyResponse deserialize(byte[] json) {
        return Throwing.supplier(() -> objectMapper.readValue(json, FreeBusyResponse.class)).get();
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
}
