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

package com.linagora.tmail.dav;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.request;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.james.util.ClassLoaderUtils;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.http.Body;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.linagora.tmail.HttpUtils;
import com.linagora.tmail.configuration.DavConfiguration;
import com.linagora.tmail.dav.request.GetCalendarByEventIdRequestBody;

public class DavServerExtension extends WireMockExtension {
    public static final String ALICE_ID = "ALICE_ID";
    public static final String ALICE = "alice@james.org";
    public static final DavUser ALICE_DAV_USER = new DavUser(ALICE_ID, ALICE);
    public static final String ALICE_CALENDAR_1 = "66e95872cf2c37001f0d2a09";
    public static final String ALICE_CALENDAR_2 = "0b4e80d7-7337-458f-852d-7ae8d72a74b2";
    public static final EventUid ALICE_VEVENT_1 = new EventUid("ab3db856-a866-4a91-99a3-c84372eaee87");
    public static final String ALICE_VEVENT_2 = "";
    public static final String ALICE_RECURRING_EVENT = "";
    public static final String ALICE_CALENDAR_OBJECT_1 = "/calendars/ALICE_ID/66e95872cf2c37001f0d2a09/sabredav-c23db537-7162-4b8d-b034-ab9436304959.ics";
    public static final String ALICE_CALENDAR_OBJECT_2 = "/calendars/ALICE_ID/66e95872cf2c37001f0d2a09/random-stuff.ics";

    public static final String DAV_ADMIN = "admin";
    public static final String DAV_ADMIN_PASSWORD = "secret123";
    private static final Duration TEN_SECONDS = Duration.ofSeconds(10);
    private static final boolean TRUST_ALL_SSL_CERTS = true;
    private static final boolean USE_REGEX = true;

    public DavServerExtension() {
        super(WireMockExtension.extensionOptions()
            .options(wireMockConfig().dynamicPort()));
    }

    @Override
    protected void onBeforeEach(WireMockRuntimeInfo wireMockRuntimeInfo) {
        super.onBeforeEach(wireMockRuntimeInfo);

        stubFor(
            propfind("/calendars/" + ALICE_ID)
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(ALICE)))
                .willReturn(
                    aResponse()
                        .withResponseBody(
                            new Body(
                                ClassLoaderUtils.getSystemResourceAsByteArray("ALICE_PROPFIND_CALENDARS_RESPONSE.xml")))
                        .withStatus(207)));

        stubFor(
          report("/calendars/%s/%s/".formatted(ALICE_ID, ALICE_CALENDAR_1))
              .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(ALICE)))
              .withHeader("Accept", equalTo("application/xml"))
              .withHeader("Depth", equalTo("1"))
              .withRequestBody(equalTo(
                  new GetCalendarByEventIdRequestBody(ALICE_VEVENT_1).value()))
              .willReturn(
                  aResponse()
                      .withResponseBody(
                          new Body(
                              ClassLoaderUtils.getSystemResourceAsByteArray("ALICE_VEVENT1_RESPONSE.xml")))
                      .withStatus(207)));

        stubFor(
            report("/calendars/%s/%s/".formatted(ALICE_ID, ALICE_CALENDAR_2))
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(ALICE)))
                .withHeader("Accept", equalTo("application/xml"))
                .withHeader("Depth", equalTo("1"))
                .withRequestBody(equalTo(
                    new GetCalendarByEventIdRequestBody(ALICE_VEVENT_1).value()))
                .willReturn(
                    aResponse()
                        .withResponseBody(
                            new Body(
                                ClassLoaderUtils.getSystemResourceAsByteArray("EMPTY_MULTISTATUS_RESPONSE.xml")))
                        .withStatus(207)));

        stubFor(
            put(ALICE_CALENDAR_OBJECT_1)
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(ALICE)))
                .withHeader("Accept", equalTo("application/xml"))
                .willReturn(noContent()));

        stubFor(
            put(ALICE_CALENDAR_OBJECT_2)
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(ALICE)))
                .withHeader("Accept", equalTo("application/xml"))
                .willReturn(notFound()));

        stubFor(
            get(ALICE_CALENDAR_OBJECT_1)
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(ALICE)))
                .willReturn(
                    aResponse()
                        .withHeader("ETag", "123")
                        .withResponseBody(
                            new Body(
                                ClassLoaderUtils.getSystemResourceAsString("VCALENDAR1.ics")))));
    }

    public void setCollectedContactExists(String openPassUserName, String openPassUserId, String collectedContactUid, boolean exists) {
        if (exists) {
            stubFor(
                get("/addressbooks/%s/collected/%s.vcf".formatted(openPassUserId, collectedContactUid))
                    .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(openPassUserName)))
                    .willReturn(
                        ok()));

        } else {
            stubFor(
                get("/addressbooks/%s/collected/%s.vcf".formatted(openPassUserId, collectedContactUid))
                    .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(openPassUserName)))
                    .willReturn(notFound()));
        }
    }

    public void setCreateCollectedContact(String openPassUserName, String openPassUserId, String collectedContactUid) {
        stubFor(
            put("/addressbooks/%s/collected/%s.vcf".formatted(openPassUserId, collectedContactUid))
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(openPassUserName)))
                .withHeader("Content-Type", equalTo("application/vcard"))
                .willReturn(created()));
    }

    public void setCreateCollectedContactAlreadyExists(String openPassUserName, String openPassUserId, String collectedContactUid) {
        stubFor(
            put("/addressbooks/%s/collected/%s.vcf".formatted(openPassUserId, collectedContactUid))
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(openPassUserName)))
                .withHeader("Content-Type", equalTo("application/vcard"))
                .willReturn(noContent()));
    }

    public void addMockOfFindingUserCalendars(String openPassUid, String username) {
        stubFor(
            propfind("/calendars/" + openPassUid)
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(username)))
                .willReturn(
                    aResponse()
                        .withResponseBody(
                            new Body(
                                ClassLoaderUtils.getSystemResourceAsByteArray("BOB_PROPFIND_CALENDARS_RESPONSE.xml")))
                        .withStatus(207)));
    }

    public void addMockOfCreatingCalendarObject(String uri, String username) {
        stubFor(
            put(uri)
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(username)))
                .willReturn(created()));
    }

    public void addMockOfDeletingCalendarObject(String uri, String username) {
        stubFor(
            delete(uri)
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(username)))
                .willReturn(noContent()));
    }

    public void assertCollectedContactExistsWasCalled(String openPassUserName, String openPassUserId, String collectedContactUid, int times) {
        verify(times,
            getRequestedFor(
                urlEqualTo("/addressbooks/%s/collected/%s.vcf".formatted(openPassUserId,
                    collectedContactUid)))
                .withHeader("Authorization",
                equalTo(createDelegatedBasicAuthenticationToken(openPassUserName))));
    }

    public void assertCreateCollectedContactWasCalled(String openPassUserName, String openPassUserId, String collectedContactUid, int times) {
        verify(times,
            putRequestedFor(
                urlEqualTo("/addressbooks/%s/collected/%s.vcf".formatted(openPassUserId,
                    collectedContactUid)))
                .withHeader("Authorization",
                    equalTo(createDelegatedBasicAuthenticationToken(openPassUserName)))
                .withHeader("Content-Type", equalTo("application/vcard")));
    }

    public void assertMockOfCreatingCalendarObjectWasCalled(String uri, String username, int times) {
        verify(times,
            putRequestedFor(urlEqualTo(uri))
                .withHeader("Authorization",
                    equalTo(createDelegatedBasicAuthenticationToken(username))));
    }

    public void assertMockOfDeletingCalendarObjectWasCalled(String uri, String username, int times) {
        verify(times,
            deleteRequestedFor(
                urlEqualTo(uri))
                .withHeader("Authorization",
                    equalTo(createDelegatedBasicAuthenticationToken(username))));
    }

    public DavConfiguration getDavConfiguration() {
        return new DavConfiguration(
            new UsernamePasswordCredentials(DAV_ADMIN, DAV_ADMIN_PASSWORD),
            URI.create(baseUrl()),
            TRUST_ALL_SSL_CERTS,
            Optional.of(TEN_SECONDS));
    }

    public static String createDelegatedBasicAuthenticationToken(String username) {
        return HttpUtils.createBasicAuthenticationToken(DAV_ADMIN + "&" + username, DAV_ADMIN_PASSWORD);
    }

    public static MappingBuilder propfind(String url) {
        return request("PROPFIND", new UrlPattern(equalTo(url), !USE_REGEX));
    }

    public static MappingBuilder report(String url) {
        return request("REPORT", new UrlPattern(equalTo(url), !USE_REGEX));
    }
}
