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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;

public record SaaSSubscriptionMessage(String internalEmail, Boolean isPaying, Boolean canUpgrade, MailLimitation mail) {
    public static class SaaSSubscriptionMessageParseException extends RuntimeException {
        SaaSSubscriptionMessageParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final Deserializer DESERIALIZER = new Deserializer();

    public static class Deserializer {
        public static SaaSSubscriptionMessage parseAMQPMessage(String messagePayload) {
            return parseAMQPMessage(messagePayload.getBytes(StandardCharsets.UTF_8));
        }

        public static SaaSSubscriptionMessage parseAMQPMessage(byte[] messagePayload) {
            try {
                return DESERIALIZER.deserialize(messagePayload);
            } catch (Exception e) {
                throw new SaaSSubscriptionMessageParseException("Failed to parse SaaS subscription message: " + new String(messagePayload, StandardCharsets.UTF_8), e);
            }
        }

        private final ObjectMapper objectMapper;

        public Deserializer() {
            this.objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }

        public SaaSSubscriptionMessage deserialize(byte[] jsonBytes) throws IOException {
            return objectMapper.readValue(jsonBytes, SaaSSubscriptionMessage.class);
        }
    }

    public record MailLimitation(Long storageQuota) {
        @JsonCreator
        public MailLimitation(@JsonProperty("storageQuota") Long storageQuota) {
            Preconditions.checkNotNull(storageQuota, "storageQuota cannot be null");

            this.storageQuota = storageQuota;
        }
    }

    @JsonCreator
    public SaaSSubscriptionMessage(@JsonProperty("internalEmail") String internalEmail,
                                   @JsonProperty("isPaying") Boolean isPaying,
                                   @JsonProperty("canUpgrade") Boolean canUpgrade,
                                   @JsonProperty("mail") MailLimitation mail) {
        Preconditions.checkNotNull(internalEmail, "internalEmail cannot be null");
        Preconditions.checkNotNull(isPaying, "isPaying cannot be null");
        Preconditions.checkNotNull(canUpgrade, "planName cannot be null");
        Preconditions.checkNotNull(mail, "mail cannot be null");

        this.internalEmail = internalEmail;
        this.isPaying = isPaying;
        this.canUpgrade = canUpgrade;
        this.mail = mail;
    }
}
