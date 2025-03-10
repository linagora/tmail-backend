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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import java.net.URI;
import java.net.URISyntaxException;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.linagora.tmail.HttpUtils;

public class WireMockOpenPaaSServerExtension extends WireMockExtension {
    public static final String ALICE_ID = "ALICE_ID";

    public static final String BOB_ID = "BOB_ID";
    public static final String BOB = "BOB";
    public static final String BAD_PASSWORD = "BAD_PASSWORD";
    public static final String GOOD_PASSWORD = "GOOD_PASSWORD";
    public static final String ERROR_MAIL = "error@lina.com";
    public static final String ALICE_EMAIL = "adoe@linagora.com";
    public static final String NOTFOUND_EMAIL = "notfound@lina.com";

    public WireMockOpenPaaSServerExtension(Builder builder) {
        super(builder);
    }

    public WireMockOpenPaaSServerExtension() {
        this(WireMockExtension.extensionOptions()
            .options(wireMockConfig().dynamicPort()));
    }

    public URI getBaseUrl() {
        try {
            return new URI(baseUrl());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onBeforeEach(WireMockRuntimeInfo wireMockRuntimeInfo) {
        super.onBeforeEach(wireMockRuntimeInfo);

        stubFor(get("/users/" + ALICE_ID)
            .withHeader("Authorization", equalTo(HttpUtils.createBasicAuthenticationToken(ALICE_ID, BAD_PASSWORD)))
                .willReturn(aResponse().withStatus(401)));

        stubFor(get("/users/" + ALICE_ID)
            .withHeader("Authorization", equalTo(HttpUtils.createBasicAuthenticationToken(ALICE_ID, GOOD_PASSWORD)))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("{\n" +
                    "  \"_id\":  \"ALICE_ID\",\n" +
                    "  \"firstname\": \"Alice\",\n" +
                    "  \"lastname\": \"DOE\",\n" +
                    "  \"preferredEmail\": \"adoe@linagora.com\",\n" +
                    "  \"emails\": [\n" +
                    "    \"adoe@linagora.com\"\n" +
                    "  ],\n" +
                    "  \"domains\": [\n" +
                    "    {\n" +
                    "      \"joined_at\": \"2020-09-03T08:16:35.682Z\",\n" +
                    "      \"domain_id\": \"ALICE_ID\"\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"states\": [],\n" +
                    "  \"avatars\": [\n" +
                    "    \"ALICE_ID\"\n" +
                    "  ],\n" +
                    "  \"main_phone\": \"01111111111\",\n" +
                    "  \"accounts\": [\n" +
                    "    {\n" +
                    "      \"timestamps\": {\n" +
                    "        \"creation\": \"2020-09-03T08:16:35.682Z\"\n" +
                    "      },\n" +
                    "      \"hosted\": true,\n" +
                    "      \"emails\": [\n" +
                    "        \"adoe@linagora.com\"\n" +
                    "      ],\n" +
                    "      \"preferredEmailIndex\": 0,\n" +
                    "      \"type\": \"email\"\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"login\": {\n" +
                    "    \"failures\": [],\n" +
                    "    \"success\": \"2024-10-04T12:59:44.469Z\"\n" +
                    "  },\n" +
                    "  \"id\": \"ALICE_ID\",\n" +
                    "  \"displayName\": \"Alice DOE\",\n" +
                    "  \"objectType\": \"user\",\n" +
                    "  \"followers\": 0,\n" +
                    "  \"followings\": 0\n" +
                    "}")));

        stubFor(get("/users/" + BOB_ID)
            .withHeader("Authorization", equalTo(HttpUtils.createBasicAuthenticationToken(BOB_ID, GOOD_PASSWORD)))
            .willReturn(aResponse()
                    .withStatus(200)
                    .withBody("{\n" +
                        "  \"_id\":  \"BOB_ID\",\n" +
                        "  \"firstname\": \"Alice\",\n" +
                        "  \"lastname\": \"DOE\",\n" +
                        "  \"preferredEmail\": \"BOB_EMAIL_IS_INVALID\",\n" +
                        "  \"emails\": [\n" +
                        "    \"BOB_EMAIL_IS_INVALID\"\n" +
                        "  ],\n" +
                        "  \"domains\": [\n" +
                        "    {\n" +
                        "      \"joined_at\": \"2020-09-03T08:16:35.682Z\",\n" +
                        "      \"domain_id\": \"BOB_ID\"\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"states\": [],\n" +
                        "  \"avatars\": [\n" +
                        "    \"BOB_ID\"\n" +
                        "  ],\n" +
                        "  \"main_phone\": \"01111111111\",\n" +
                        "  \"accounts\": [\n" +
                        "    {\n" +
                        "      \"timestamps\": {\n" +
                        "        \"creation\": \"2020-09-03T08:16:35.682Z\"\n" +
                        "      },\n" +
                        "      \"hosted\": true,\n" +
                        "      \"emails\": [\n" +
                        "        \"adoe@linagora.com\"\n" +
                        "      ],\n" +
                        "      \"preferredEmailIndex\": 0,\n" +
                        "      \"type\": \"email\"\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"login\": {\n" +
                        "    \"failures\": [],\n" +
                        "    \"success\": \"2024-10-04T12:59:44.469Z\"\n" +
                        "  },\n" +
                        "  \"id\": \"BOB_ID\",\n" +
                        "  \"displayName\": \"Alice DOE\",\n" +
                        "  \"objectType\": \"user\",\n" +
                        "  \"followers\": 0,\n" +
                        "  \"followings\": 0\n" +
                        "}")));
        setSearchEmailExist(ALICE_EMAIL, ALICE_ID);
        setSearchEmailNotFound(NOTFOUND_EMAIL);

        stubFor(get(urlEqualTo("/users?email=" + ERROR_MAIL))
            .withHeader("Authorization", equalTo(HttpUtils.createBasicAuthenticationToken(ALICE_ID, GOOD_PASSWORD)))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("503 Temporary error")));
    }

    public void setSearchEmailExist(String emailQuery, String openPassUidExpected) {
        stubFor(get(urlEqualTo("/users?email=" + emailQuery))
            .withHeader("Authorization", equalTo(HttpUtils.createBasicAuthenticationToken(ALICE_ID, GOOD_PASSWORD)))
            .willReturn(aResponse().withStatus(200)
                .withBody("[\n" +
                    "    {\n" +
                    "        \"_id\": \"" + openPassUidExpected + "\",\n" +
                    "        \"firstname\": \"John1\",\n" +
                    "        \"lastname\": \"Doe1\",\n" +
                    "        \"preferredEmail\": \"" + emailQuery + "\",\n" +
                    "        \"emails\": [\n" +
                    "            \"" + emailQuery + "\"\n" +
                    "        ],\n" +
                    "        \"domains\": [\n" +
                    "            {\n" +
                    "                \"joined_at\": \"2024-12-17T13:00:22.766Z\",\n" +
                    "                \"domain_id\": \"676175e3aea7130059d339b2\"\n" +
                    "            }\n" +
                    "        ],\n" +
                    "        \"states\": [],\n" +
                    "        \"avatars\": [],\n" +
                    "        \"id\": \"" + openPassUidExpected + "\",\n" +
                    "        \"displayName\": \"John1 Doe1\",\n" +
                    "        \"objectType\": \"user\"\n" +
                    "    }\n" +
                    "]")));
    }

    public void setSearchEmailNotFound(String emailQuery) {
        stubFor(get(urlEqualTo("/users?email=" + emailQuery))
            .withHeader("Authorization", equalTo(HttpUtils.createBasicAuthenticationToken(ALICE_ID, GOOD_PASSWORD)))
            .willReturn(aResponse().withStatus(200).withBody("[]")));
    }
}
