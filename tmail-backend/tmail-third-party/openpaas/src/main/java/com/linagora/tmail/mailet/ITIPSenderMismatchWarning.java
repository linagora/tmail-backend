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

package com.linagora.tmail.mailet;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;

import org.apache.james.core.MailAddress;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linagora.tmail.james.jmap.calendar.InMemoryAttach;

import net.fortuna.ical4j.data.ContentHandlerContext;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Property;

/**
 * Warns the recipients of an invitation whose mail does not come from its organizer, on behalf of the CalDavCollect
 * mailets.
 *
 * These mailets do not hand such an invitation to esn-sabre, yet the user may still add it to their calendar from the
 * mail. When the sender of the mail, as computed by ICALToJsonAttribute, is a remote address not aligned (see
 * {@link ITIPSenderAlignment}) with the organizer of an invitation it carries, the mail gets:
 *
 * <ul>
 *     <li>{@value #TWP_MESSAGE_HEADER}: {@code level:warn code:}{@value #WARNING_CODE} followed by a fallback text,
 *     displayed as a warning banner by the clients;</li>
 *     <li>{@value #MISMATCH_HEADER}: {@code organizer=<address>; sender=<address>}, for the clients and tools needing the
 *     addresses.</li>
 * </ul>
 *
 * Local senders are trusted: their identity is verified on submission, a mismatch then is an assistant sending for the
 * organizer. A remote sender claiming a local From is expected to be rejected upstream, by DMARC. REPLY messages are left alone: no invitation is to be added from them.
 *
 * So that these warnings only come from the server, the {@value #MISMATCH_HEADER} headers and the
 * {@value #TWP_MESSAGE_HEADER} ones of {@value #WARNING_CODE} a mail holds on arrival are first removed. The
 * {@value #TWP_MESSAGE_HEADER} of other codes are left untouched.
 */
public final class ITIPSenderMismatchWarning {
    public static final String TWP_MESSAGE_HEADER = "X-TWP-Message";
    public static final String MISMATCH_HEADER = "X-Twake-Calendar-Sender-Mismatch";
    public static final String WARNING_CODE = "calendar-sender-mismatch";

    private static final Logger LOGGER = LoggerFactory.getLogger(ITIPSenderMismatchWarning.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    @SuppressWarnings("unchecked")
    private static final Class<Map<String, AttributeValue<byte[]>>> MAP_STRING_JSON_BYTES_CLASS = (Class<Map<String, AttributeValue<byte[]>>>) (Object) Map.class;

    record Mismatch(MailAddress sender, Optional<MailAddress> organizer) {
        String warning() {
            return "level:warn code:" + WARNING_CODE + " " + organizer
                .map(address -> "This invitation is organized by " + address.asString() + " but was sent by " + sender.asString() + ".")
                .orElseGet(() -> "This invitation was not sent by its organizer but by " + sender.asString() + ".");
        }

        String addresses() {
            return organizer.map(address -> "organizer=<" + address.asString() + ">; ").orElse("")
                + "sender=<" + sender.asString() + ">";
        }
    }

    private ITIPSenderMismatchWarning() {
    }

    public static void apply(Mail mail, AttributeName sourceAttributeName, AlignmentMode alignmentMode, MailetContext mailetContext) {
        try {
            MimeMessage message = mail.getMessage();
            if (message == null) {
                return;
            }
            boolean removedReceivedWarnings = removeReceivedWarnings(message);
            Optional<Mismatch> mismatch = firstMismatch(mail, sourceAttributeName, alignmentMode, mailetContext);
            if (mismatch.isPresent()) {
                message.addHeader(TWP_MESSAGE_HEADER, mismatch.get().warning());
                message.addHeader(MISMATCH_HEADER, mismatch.get().addresses());
            }
            if (removedReceivedWarnings || mismatch.isPresent()) {
                message.saveChanges();
            }
        } catch (MessagingException e) {
            LOGGER.warn("Can not set the sender mismatch warning of mail {}", mail.getName(), e);
        }
    }

    // The warnings of this kind a mail holds on arrival were written by its sender
    private static boolean removeReceivedWarnings(MimeMessage message) throws MessagingException {
        boolean hadMismatchHeader = message.getHeader(MISMATCH_HEADER) != null;
        message.removeHeader(MISMATCH_HEADER);

        String[] twpMessages = Optional.ofNullable(message.getHeader(TWP_MESSAGE_HEADER)).orElse(new String[0]);
        List<String> othersWarnings = Arrays.stream(twpMessages)
            .filter(value -> !isOwnWarning(value))
            .toList();
        if (othersWarnings.size() == twpMessages.length) {
            return hadMismatchHeader;
        }
        message.removeHeader(TWP_MESSAGE_HEADER);
        for (String warning : othersWarnings) {
            message.addHeader(TWP_MESSAGE_HEADER, warning);
        }
        return true;
    }

    // The fields of a X-TWP-Message are space separated: level:<level> code:<code> <fallback text>
    private static boolean isOwnWarning(String twpMessage) {
        return Arrays.stream(MimeUtility.unfold(twpMessage).trim().split("\\s+"))
            .limit(2)
            .anyMatch(("code:" + WARNING_CODE)::equalsIgnoreCase);
    }

    private static Optional<Mismatch> firstMismatch(Mail mail, AttributeName sourceAttributeName, AlignmentMode alignmentMode, MailetContext mailetContext) {
        try {
            return AttributeUtils.getValueAndCastFromMail(mail, sourceAttributeName, MAP_STRING_JSON_BYTES_CLASS)
                .stream()
                .flatMap(jsons -> jsons.values().stream())
                .map(AttributeValue::getValue)
                .flatMap(json -> mismatchOf(json, mail.getName(), alignmentMode, mailetContext).stream())
                .findFirst();
        } catch (ClassCastException e) {
            LOGGER.error("Attribute {} is not Map<String, AttributeValue<byte[]> in mail {}", sourceAttributeName, mail.getName(), e);
            return Optional.empty();
        }
    }

    private static Optional<Mismatch> mismatchOf(byte[] json, String mailName, AlignmentMode alignmentMode, MailetContext mailetContext) {
        try {
            JsonNode jsonNode = OBJECT_MAPPER.readTree(new String(json, StandardCharsets.UTF_8));
            String icalContent = jsonNode.path("ical").asText();
            String sender = jsonNode.path("sender").asText();
            if (icalContent.isEmpty() || sender.isEmpty()) {
                return Optional.empty();
            }
            MailAddress senderAddress = new MailAddress(sender);
            if (mailetContext.isLocalServer(senderAddress.getDomain())) {
                return Optional.empty();
            }
            Calendar calendar = InMemoryAttach.calendarBuilder(new ContentHandlerContext()).build(new StringReader(icalContent));
            if (isReply(calendar) || ITIPSenderAlignment.isSatisfied(alignmentMode, senderAddress, calendar)) {
                return Optional.empty();
            }
            return Optional.of(new Mismatch(senderAddress, ITIPSenderAlignment.singleOrganizer(calendar)));
        } catch (Exception e) {
            LOGGER.warn("Can not check the sender alignment of a calendar in mail {}", mailName, e);
            return Optional.empty();
        }
    }

    private static boolean isReply(Calendar calendar) {
        return calendar.getProperty(Property.METHOD)
            .map(Property::getValue)
            .map("REPLY"::equalsIgnoreCase)
            .orElse(false);
    }
}
