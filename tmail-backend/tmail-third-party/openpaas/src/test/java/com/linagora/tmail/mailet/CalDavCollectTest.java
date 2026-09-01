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

import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.linagora.tmail.dav.DavServerExtension.ALICE;
import static com.linagora.tmail.dav.DavServerExtension.ALICE_DAV_USER;
import static com.linagora.tmail.dav.DavServerExtension.ALICE_ID;
import static com.linagora.tmail.dav.DavServerExtension.createDelegatedBasicAuthenticationToken;
import static com.linagora.tmail.dav.DavServerExtension.itip;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.linagora.tmail.dav.DavClient;
import com.linagora.tmail.dav.DavClientException;
import com.linagora.tmail.dav.DavServerExtension;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import reactor.core.publisher.Mono;

class CalDavCollectTest {
    private static final String ORGANIZER = "bob@james.org";
    private static final String ICS = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//Twake Mail//EN
        METHOD:REQUEST
        BEGIN:VEVENT
        UID:ab3db856-a866-4a91-99a3-c84372eaee87
        DTSTAMP:20250101T100000Z
        DTSTART:20250101T110000Z
        DTEND:20250101T120000Z
        SUMMARY:Sprint planning
        ORGANIZER:mailto:%s
        ATTENDEE:mailto:%s
        END:VEVENT
        END:VCALENDAR
        """.formatted(ORGANIZER, ALICE);
    private static final String SABRE_ERROR_BODY = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:error xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns">
          <s:exception>Sabre\\VObject\\ITip\\ITipException</s:exception>
          <s:message>The supplied message must have a valid METHOD property</s:message>
        </d:error>""";

    @RegisterExtension
    static DavServerExtension davServerExtension = new DavServerExtension();

    private CalDavCollect mailet;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() throws Exception {
        mailet = new CalDavCollect(new DavClient(davServerExtension.getDavConfiguration()), username -> Mono.just(ALICE_DAV_USER));
        mailet.init(FakeMailetConfig.builder().mailetName("CalDavCollect").build());

        logger = (Logger) LoggerFactory.getLogger(CalDavCollect.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
    }

    @Test
    void serviceShouldLogSabreResponseAsMdcFieldWhenDavServerRejectsItipRequest() throws Exception {
        davServerExtension.stubFor(
            itip("/calendars/" + ALICE_ID)
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(ALICE)))
                .willReturn(badRequest().withBody(SABRE_ERROR_BODY)));

        mailet.service(mailWithCalendar());

        assertThat(errorLogs())
            .hasSize(1)
            .first()
            .satisfies(event -> {
                assertThat(event.getFormattedMessage()).isEqualTo("Error while handling calendar in mail mail1 with recipient " + ALICE);
                assertThat(event.getMDCPropertyMap()).containsEntry(DavClientException.SABRE_RESPONSE_MDC_KEY, SABRE_ERROR_BODY);
            });
    }

    @Test
    void serviceShouldNotLeakSabreResponseMdcFieldAfterLogging() throws Exception {
        davServerExtension.stubFor(
            itip("/calendars/" + ALICE_ID)
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(ALICE)))
                .willReturn(badRequest().withBody(SABRE_ERROR_BODY)));

        mailet.service(mailWithCalendar());

        assertThat(org.slf4j.MDC.get(DavClientException.SABRE_RESPONSE_MDC_KEY)).isNull();
    }

    private List<ILoggingEvent> errorLogs() {
        return logAppender.list.stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .toList();
    }

    private Mail mailWithCalendar() throws Exception {
        ObjectNode json = new ObjectMapper().createObjectNode();
        json.put("ical", ICS);
        json.put("recipient", ALICE);
        json.put("sender", ORGANIZER);
        byte[] jsonBytes = json.toString().getBytes(StandardCharsets.UTF_8);

        return FakeMail.builder()
            .name("mail1")
            .attribute(new Attribute(
                AttributeName.of(CalDavCollect.DEFAULT_SOURCE_ATTRIBUTE_NAME),
                AttributeValue.of(ImmutableMap.<String, AttributeValue<?>>of("ics1", AttributeValue.of(jsonBytes)))))
            .build();
    }
}
