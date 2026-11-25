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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.apache.james.core.Domain;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;

class ITIPSenderMismatchWarningTest {
    private static final String LOCAL_DOMAIN = "company.org";
    private static final String ORGANIZER = "ceo@company.org";
    private static final String EXTERNAL_SENDER = "attacker@evil.example";
    private static final String LOCAL_ASSISTANT = "assistant@company.org";

    private static String ics(String method, String organizer) {
        return """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Twake Mail//EN
            METHOD:%s
            BEGIN:VEVENT
            UID:event-1
            DTSTAMP:20250101T000000Z
            DTSTART:20250102T100000Z
            DTEND:20250102T110000Z
            ORGANIZER:mailto:%s
            ATTENDEE;PARTSTAT=NEEDS-ACTION:mailto:alice@company.org
            END:VEVENT
            END:VCALENDAR
            """.formatted(method, organizer).replace("\n", "\r\n");
    }

    private MailetContext mailetContext;

    @BeforeEach
    void setUp() {
        mailetContext = mock(MailetContext.class);
        when(mailetContext.isLocalServer(any())).thenAnswer(invocation -> invocation.<Domain>getArgument(0).equals(Domain.of(LOCAL_DOMAIN)));
    }

    private void apply(Mail mail) {
        apply(mail, AlignmentMode.STRICT);
    }

    private void apply(Mail mail, AlignmentMode alignmentMode) {
        ITIPSenderMismatchWarning.apply(mail, AttributeName.of(CalDavCollect.DEFAULT_SOURCE_ATTRIBUTE_NAME), alignmentMode, mailetContext);
    }

    private Mail mailWithCalendar(String ics, String sender) throws Exception {
        ObjectNode json = new ObjectMapper().createObjectNode();
        json.put("ical", ics);
        json.put("recipient", "alice@company.org");
        json.put("sender", sender);

        return FakeMail.builder()
            .name("mail1")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("Invitation")
                .setText("An invitation"))
            .attribute(new Attribute(
                AttributeName.of(CalDavCollect.DEFAULT_SOURCE_ATTRIBUTE_NAME),
                AttributeValue.of(ImmutableMap.<String, AttributeValue<?>>of("ics1",
                    AttributeValue.of(json.toString().getBytes(StandardCharsets.UTF_8))))))
            .build();
    }

    @Test
    void shouldWarnWhenAnExternalSenderIsNotTheOrganizer() throws Exception {
        Mail mail = mailWithCalendar(ics("REQUEST", ORGANIZER), EXTERNAL_SENDER);

        apply(mail);

        assertThat(mail.getMessage().getHeader(ITIPSenderMismatchWarning.TWP_MESSAGE_HEADER))
            .containsExactly("level:warn code:calendar-sender-mismatch This invitation is organized by " + ORGANIZER
                + " but was sent by " + EXTERNAL_SENDER + ".");
        assertThat(mail.getMessage().getHeader(ITIPSenderMismatchWarning.MISMATCH_HEADER))
            .containsExactly("organizer=<" + ORGANIZER + ">; sender=<" + EXTERNAL_SENDER + ">");
    }

    @Test
    void shouldWarnOnceWhenSeveralRecipientsGetTheInvitation() throws Exception {
        ObjectNode json = new ObjectMapper().createObjectNode();
        json.put("ical", ics("REQUEST", ORGANIZER));
        json.put("sender", EXTERNAL_SENDER);
        byte[] jsonBytes = json.toString().getBytes(StandardCharsets.UTF_8);
        Mail mail = FakeMail.builder()
            .name("mail1")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder().setText("An invitation"))
            .attribute(new Attribute(
                AttributeName.of(CalDavCollect.DEFAULT_SOURCE_ATTRIBUTE_NAME),
                AttributeValue.of(ImmutableMap.<String, AttributeValue<?>>of(
                    "alice", AttributeValue.of(jsonBytes),
                    "bob", AttributeValue.of(jsonBytes)))))
            .build();

        apply(mail);

        assertThat(mail.getMessage().getHeader(ITIPSenderMismatchWarning.TWP_MESSAGE_HEADER)).hasSize(1);
    }

    @Test
    void shouldNotWarnWhenTheOrganizerSendsTheInvitation() throws Exception {
        Mail mail = mailWithCalendar(ics("REQUEST", EXTERNAL_SENDER), EXTERNAL_SENDER);

        apply(mail);

        assertThat(mail.getMessage().getHeader(ITIPSenderMismatchWarning.TWP_MESSAGE_HEADER)).isNull();
        assertThat(mail.getMessage().getHeader(ITIPSenderMismatchWarning.MISMATCH_HEADER)).isNull();
    }

    @Test
    void shouldNotWarnWhenALocalSenderIsNotTheOrganizer() throws Exception {
        Mail mail = mailWithCalendar(ics("REQUEST", ORGANIZER), LOCAL_ASSISTANT);

        apply(mail);

        assertThat(mail.getMessage().getHeader(ITIPSenderMismatchWarning.TWP_MESSAGE_HEADER)).isNull();
    }

    @Test
    void shouldNotWarnOnAReply() throws Exception {
        Mail mail = mailWithCalendar(ics("REPLY", ORGANIZER), EXTERNAL_SENDER);

        apply(mail);

        assertThat(mail.getMessage().getHeader(ITIPSenderMismatchWarning.TWP_MESSAGE_HEADER)).isNull();
    }

    @Test
    void shouldNotWarnOnAMailWithoutCalendar() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail1")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder().setText("No invitation"))
            .build();

        apply(mail);

        assertThat(mail.getMessage().getHeader(ITIPSenderMismatchWarning.TWP_MESSAGE_HEADER)).isNull();
    }

    @Test
    void shouldNotWarnOnAMalformedCalendar() throws Exception {
        Mail mail = mailWithCalendar("BEGIN:VCALENDAR\r\nnot a calendar", EXTERNAL_SENDER);

        apply(mail);

        assertThat(mail.getMessage().getHeader(ITIPSenderMismatchWarning.TWP_MESSAGE_HEADER)).isNull();
    }

    @Test
    void shouldFollowTheAlignmentModeOfCalDavCollect() throws Exception {
        Mail mail = mailWithCalendar(ics("REQUEST", "boss@partner.example"), "secretary@partner.example");

        apply(mail, AlignmentMode.SAME_DOMAIN);

        assertThat(mail.getMessage().getHeader(ITIPSenderMismatchWarning.TWP_MESSAGE_HEADER)).isNull();
    }


    @Test
    void shouldRemoveTheWarningsOfItsOwnASenderWrote() throws Exception {
        Mail mail = mailWithCalendar(ics("REQUEST", EXTERNAL_SENDER), EXTERNAL_SENDER);
        mail.getMessage().addHeader(ITIPSenderMismatchWarning.TWP_MESSAGE_HEADER, "level:warn code:calendar-sender-mismatch Forged");
        mail.getMessage().addHeader(ITIPSenderMismatchWarning.MISMATCH_HEADER, "organizer=<x@y.org>; sender=<z@y.org>");

        apply(mail);

        assertThat(mail.getMessage().getHeader(ITIPSenderMismatchWarning.TWP_MESSAGE_HEADER)).isNull();
        assertThat(mail.getMessage().getHeader(ITIPSenderMismatchWarning.MISMATCH_HEADER)).isNull();
    }

    @Test
    void shouldReplaceTheWarningsOfItsOwnASenderWroteByItsOwn() throws Exception {
        Mail mail = mailWithCalendar(ics("REQUEST", ORGANIZER), EXTERNAL_SENDER);
        mail.getMessage().addHeader(ITIPSenderMismatchWarning.MISMATCH_HEADER, "organizer=<x@y.org>; sender=<z@y.org>");

        apply(mail);

        assertThat(mail.getMessage().getHeader(ITIPSenderMismatchWarning.MISMATCH_HEADER))
            .containsExactly("organizer=<" + ORGANIZER + ">; sender=<" + EXTERNAL_SENDER + ">");
    }

    @Test
    void shouldKeepTheWarningsOfOtherCodes() throws Exception {
        Mail mail = mailWithCalendar(ics("REQUEST", ORGANIZER), EXTERNAL_SENDER);
        mail.getMessage().addHeader(ITIPSenderMismatchWarning.TWP_MESSAGE_HEADER, "level:info code:suspicious-sender From outside");

        apply(mail);

        assertThat(mail.getMessage().getHeader(ITIPSenderMismatchWarning.TWP_MESSAGE_HEADER))
            .containsExactly("level:info code:suspicious-sender From outside",
                "level:warn code:calendar-sender-mismatch This invitation is organized by " + ORGANIZER + " but was sent by " + EXTERNAL_SENDER + ".");
    }

    @Test
    void shouldRemoveTheWarningsOfItsOwnFromAMailWithoutCalendar() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail1")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader(ITIPSenderMismatchWarning.TWP_MESSAGE_HEADER, "level:warn code:calendar-sender-mismatch Forged")
                .setText("No invitation"))
            .build();

        apply(mail);

        assertThat(mail.getMessage().getHeader(ITIPSenderMismatchWarning.TWP_MESSAGE_HEADER)).isNull();
    }
}
