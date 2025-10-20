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
 *******************************************************************/

package com.linagora.tmail.saas.rabbitmq.subscription;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linagora.tmail.saas.rabbitmq.subscription.SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage;
import com.linagora.tmail.saas.rabbitmq.subscription.SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage;

public class SaaSSubscriptionDeserializer {
    public static class SaaSSubscriptionMessageParseException extends RuntimeException {
        public SaaSSubscriptionMessageParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final SaaSSubscriptionDeserializer DESERIALIZER = new SaaSSubscriptionDeserializer();

    public static SaaSSubscriptionMessage parseAMQPUserMessage(String messagePayload) {
        return parseAMQPUserMessage(messagePayload.getBytes(StandardCharsets.UTF_8));
    }

    public static SaaSSubscriptionMessage parseAMQPUserMessage(byte[] messagePayload) {
        try {
            return DESERIALIZER.deserializeUserMessage(messagePayload);
        } catch (Exception e) {
            throw new SaaSSubscriptionMessageParseException("Failed to parse SaaS subscription message: " + new String(messagePayload, StandardCharsets.UTF_8), e);
        }
    }

    public static SaaSDomainSubscriptionMessage parseAMQPDomainMessage(String messagePayload) {
        return parseAMQPDomainMessage(messagePayload.getBytes(StandardCharsets.UTF_8));
    }

    public static SaaSDomainSubscriptionMessage parseAMQPDomainMessage(byte[] messagePayload) {
        try {
            return DESERIALIZER.deserializeDomainMessage(messagePayload);
        } catch (Exception e) {
            throw new SaaSSubscriptionMessageParseException("Failed to parse SaaS subscription domain message: " + new String(messagePayload, StandardCharsets.UTF_8), e);
        }
    }

    private static final String ENABLED_FIELD = "enabled";

    private final ObjectMapper objectMapper;

    public SaaSSubscriptionDeserializer() {
        this.objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public SaaSSubscriptionMessage deserializeUserMessage(byte[] jsonBytes) throws IOException {
        return objectMapper.readValue(jsonBytes, SaaSSubscriptionMessage.class);
    }

    public SaaSDomainSubscriptionMessage deserializeDomainMessage(byte[] jsonBytes) throws IOException {
        JsonNode root = objectMapper.readTree(jsonBytes);

        if (root.has(ENABLED_FIELD)) {
            return deserializeCancelDomainMessage(jsonBytes);
        }
        return deserializeValidDomainMessage(jsonBytes);
    }

    public SaaSDomainCancelSubscriptionMessage deserializeCancelDomainMessage(byte[] jsonBytes) throws IOException {
        return objectMapper.readValue(jsonBytes, SaaSDomainCancelSubscriptionMessage.class);
    }

    public SaaSDomainValidSubscriptionMessage deserializeValidDomainMessage(byte[] jsonBytes) throws IOException {
        return objectMapper.readValue(jsonBytes, SaaSDomainValidSubscriptionMessage.class);
    }
}
