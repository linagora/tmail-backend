package com.linagora.tmail.dav;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
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

import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.james.util.ClassLoaderUtils;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.http.Body;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.linagora.tmail.HttpUtils;
import com.linagora.tmail.configuration.DavConfiguration;

public class DavServerExtension extends WireMockExtension {
    public static final String ALICE_ID = "ALICE_ID";
    public static final String ALICE = "ALICE";
    public static final String ALICE_CALENDAR_1 = "66e95872cf2c37001f0d2a09";
    public static final String ALICE_CALENDAR_2 = "0b4e80d7-7337-458f-852d-7ae8d72a74b2";

    public static final String BOB_ID = "BOB_ID";
    public static final String BOB = "BOB";

    public static final String DAV_ADMIN = "admin";
    public static final String DAV_ADMIN_PASSWORD = "secret123";

    public DavServerExtension(Builder builder) {
        super(builder);
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

    public DavConfiguration getCardDavConfiguration() {
        return new DavConfiguration(
            new UsernamePasswordCredentials(DAV_ADMIN, DAV_ADMIN_PASSWORD),
            URI.create(baseUrl()),
            Optional.of(true),
            Optional.of(Duration.ofSeconds(10)));
    }

    public static String createDelegatedBasicAuthenticationToken(String username) {
        return HttpUtils.createBasicAuthenticationToken(DAV_ADMIN + "&" + username, DAV_ADMIN_PASSWORD);
    }

    static MappingBuilder propfind(String url) {
        return request("PROPFIND", new UrlPattern(equalTo(url), false));
    }

    private static MappingBuilder report(String url) {
        return request("REPORT", new UrlPattern(equalTo(url), false));
    }
}
