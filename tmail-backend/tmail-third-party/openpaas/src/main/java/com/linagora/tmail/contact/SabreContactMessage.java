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

import static com.linagora.tmail.contact.SabreContactMessage.AMQPMessage.DESERIALIZER;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.MailAddress;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.linagora.tmail.james.jmap.contact.ContactFields;

import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.property.SimpleProperty;

public record SabreContactMessage(String openPaasUserId,
                                  VCard vcard) {
    public static class SabreContactParseException extends RuntimeException {
        SabreContactParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public record AMQPMessage(@JsonProperty("path") String path,
                              @JsonProperty("owner") Optional<String> owner,
                              @JsonProperty("carddata") Optional<String> cardData) {

        public static final Deserializer DESERIALIZER = new Deserializer();

        public static class Deserializer {
            private final ObjectMapper objectMapper;

            public Deserializer() {
                this.objectMapper = new ObjectMapper()
                    .registerModule(new Jdk8Module())
                    .registerModule(new GuavaModule());
                objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            }

            public AMQPMessage deserialize(byte[] jsonBytes) throws IOException {
                return objectMapper.readValue(jsonBytes, AMQPMessage.class);
            }
        }

        public boolean hasCardData() {
            return cardData.isPresent() && owner.isPresent();
        }
    }

    public SabreContactMessage {
        Preconditions.checkNotNull(vcard);
        Preconditions.checkNotNull(openPaasUserId);
    }

    public static Optional<SabreContactMessage> parseAMQPMessage(String messagePayload) {
        return parseAMQPMessage(messagePayload.getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<SabreContactMessage> parseAMQPMessage(byte[] messagePayload) {
        try {
            AMQPMessage contactMessage = DESERIALIZER.deserialize(messagePayload);
            if (contactMessage.hasCardData()) {
                String openPaasUserId = StringUtils.substringAfterLast(contactMessage.owner().get(), "/");
                VCard vCard = Ezvcard.parse(contactMessage.cardData().get()).first();
                Preconditions.checkArgument(StringUtils.isNotBlank(openPaasUserId), "Can not parse openPaasUserId from %s", contactMessage.owner().get());
                Preconditions.checkArgument(vCard != null, "Can not parse vCard from %s", contactMessage.cardData().get());
                Preconditions.checkArgument(vCard.getUid() != null, "Can not parse vCard uid from %s", contactMessage.cardData().get());
                return Optional.of(new SabreContactMessage(openPaasUserId, vCard));
            }
            return Optional.empty();
        } catch (Exception e) {
            throw new SabreContactParseException(
                "Failed to parse contact message: " + new String(messagePayload, StandardCharsets.UTF_8), e);
        }
    }

    public List<MailAddress> getMailAddresses() {
        return vcard.getEmails().stream()
            .map(email -> StringUtils.replace(email.getValue(), "mailto:", StringUtils.EMPTY))
            .map(Throwing.function(MailAddress::new))
            .toList();
    }

    public List<ContactFields> getContactFields() {
        String fullName = Optional.ofNullable(vcard.getFormattedName()).map(SimpleProperty::getValue)
            .orElse(StringUtils.EMPTY);
        return getMailAddresses()
            .stream()
            .map(mailAddress -> ContactFields.of(mailAddress, fullName))
            .toList();
    }

    public String getVCardUid() {
        return Optional.ofNullable(vcard.getUid())
            .map(SimpleProperty::getValue).orElseThrow(() -> new IllegalArgumentException("VCard uid is null"));
    }
}
