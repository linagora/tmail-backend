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
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.MailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.linagora.tmail.james.jmap.contact.ContactFields;

import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.property.SimpleProperty;

/**
 * @param previousVcard the card before an update, when the publisher sends it (`oldcarddata`). Optional: absent
 *                      from older publishers, and an unreadable one is ignored rather than failing the message.
 */
public record SabreContactMessage(Owner owner,
                                  VCard vcard,
                                  Optional<VCard> previousVcard) {

    private static final Logger LOGGER = LoggerFactory.getLogger(SabreContactMessage.class);
    /**
     * Cards are written by users through CardDAV and published as is by Sabre. Real cards, embedded photo included,
     * stay below it.
     */
    @VisibleForTesting
    static final int MAX_CARD_DATA_LENGTH = Integer.getInteger("tmail.sabre.contact.max.card.length", 1024 * 1024);
    /**
     * ez-vcard matches each LABEL against every ADR of the card, allocating at each step: a card of a few hundred KB
     * holding tens of thousands of both takes billions of steps to parse. Real cards hold a handful of each.
     */
    @VisibleForTesting
    static final long MAX_LABEL_ADDRESS_PAIRS = 10_000;
    private static final Pattern NEWLINE = Pattern.compile("\\r\\n|\\r|\\n");

    public sealed interface Owner permits Owner.UserOwner, Owner.DomainOwner {
        String id();

        record UserOwner(String id) implements Owner {}

        record DomainOwner(String id) implements Owner {}

        static Optional<Owner> parse(String ownerPath) {
            if (StringUtils.isBlank(ownerPath)) {
                return Optional.empty();
            }
            String id = StringUtils.substringAfterLast(ownerPath, "/");
            if (ownerPath.contains("/users/")) {
                return Optional.of(new UserOwner(id));
            }
            if (ownerPath.contains("/domains/")) {
                return Optional.of(new DomainOwner(id));
            }
            return Optional.empty();
        }
    }

    public static class SabreContactParseException extends RuntimeException {
        SabreContactParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public record AMQPMessage(@JsonProperty("path") String path,
                              @JsonProperty("owner") Optional<String> owner,
                              @JsonProperty("carddata") Optional<String> cardData,
                              @JsonProperty("oldcarddata") Optional<String> oldCardData) {

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
        Preconditions.checkNotNull(owner);
        Preconditions.checkNotNull(previousVcard);
    }

    public SabreContactMessage(Owner owner, VCard vcard) {
        this(owner, vcard, Optional.empty());
    }

    public static Optional<SabreContactMessage> parseAMQPMessage(String messagePayload) {
        return parseAMQPMessage(messagePayload.getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<SabreContactMessage> parseAMQPMessage(byte[] messagePayload) {
        try {
            AMQPMessage contactMessage = DESERIALIZER.deserialize(messagePayload);
            if (contactMessage.hasCardData()) {
                String ownerPath = contactMessage.owner().get();
                Optional<Owner> maybeOwner = Owner.parse(ownerPath);
                if (maybeOwner.isEmpty()) {
                    if (StringUtils.isNotBlank(ownerPath)) {
                        LOGGER.warn("Unknown owner type in owner path '{}', ignoring message", ownerPath);
                    }
                    return Optional.empty();
                }
                Owner owner = maybeOwner.get();
                Preconditions.checkArgument(StringUtils.isNotBlank(owner.id()), "Can not parse owner id from %s", ownerPath);
                String cardData = contactMessage.cardData().get();
                checkCanBeParsed(cardData);
                VCard vCard = Ezvcard.parse(cardData).first();
                Preconditions.checkArgument(vCard != null, "Can not parse the vCard of %s", contactMessage.path());
                Preconditions.checkArgument(vCard.getUid() != null, "Can not parse the vCard uid of %s", contactMessage.path());
                return Optional.of(new SabreContactMessage(owner, vCard, parsePreviousVCard(contactMessage.oldCardData())));
            }
            return Optional.empty();
        } catch (Exception e) {
            // The payload holds the personal data of the card: it is not logged
            throw new SabreContactParseException(
                "Failed to parse contact message of " + messagePayload.length + " bytes", e);
        }
    }

    /**
     * @throws IllegalArgumentException when ez-vcard could not parse the card in bounded time
     */
    @VisibleForTesting
    static void checkCanBeParsed(String cardData) {
        Preconditions.checkArgument(cardData.length() <= MAX_CARD_DATA_LENGTH,
            "Card data of %s characters exceeds %s", cardData.length(), MAX_CARD_DATA_LENGTH);
        long labels = 0;
        long addresses = 0;
        for (String line : NEWLINE.split(cardData)) {
            switch (propertyName(line)) {
                case "LABEL" -> labels++;
                case "ADR" -> addresses++;
                default -> { }
            }
        }
        Preconditions.checkArgument(labels * addresses <= MAX_LABEL_ADDRESS_PAIRS,
            "Card with %s LABEL and %s ADR properties: too costly to parse", labels, addresses);
    }

    // The name of the property a content line starts, without its group (eg item1.ADR), empty for a folded line
    private static String propertyName(String line) {
        if (line.isEmpty() || line.charAt(0) == ' ' || line.charAt(0) == '\t') {
            return "";
        }
        int end = StringUtils.indexOfAny(line, ';', ':');
        String name = end < 0 ? line : line.substring(0, end);
        return StringUtils.substringAfterLast("." + name, ".").toUpperCase(Locale.ROOT);
    }

    private static Optional<VCard> parsePreviousVCard(Optional<String> oldCardData) {
        return oldCardData
            .filter(StringUtils::isNotBlank)
            .flatMap(cardData -> {
                try {
                    checkCanBeParsed(cardData);
                    return Optional.ofNullable(Ezvcard.parse(cardData).first());
                } catch (Exception e) {
                    LOGGER.warn("Ignoring unreadable previous card data in contact message", e);
                    return Optional.empty();
                }
            });
    }

    public List<MailAddress> getMailAddresses() {
        return vcard.getEmails().stream()
            .map(email -> StringUtils.replace(email.getValue(), "mailto:", StringUtils.EMPTY))
            .map(Throwing.function(MailAddress::new))
            .distinct()
            .toList();
    }

    /**
     * The valid email addresses of the card before the update, empty when unknown. Invalid ones are skipped: the
     * previous card only serves to find addresses to remove, it must not fail the message.
     */
    public List<MailAddress> getPreviousMailAddresses() {
        return previousVcard.stream()
            .flatMap(previous -> previous.getEmails().stream())
            .map(email -> StringUtils.replace(email.getValue(), "mailto:", StringUtils.EMPTY))
            .flatMap(address -> {
                try {
                    return Stream.of(new MailAddress(address));
                } catch (Exception e) {
                    return Stream.empty();
                }
            })
            .distinct()
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
