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

package com.linagora.tmail.common;

import static io.restassured.RestAssured.given;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.jmap.rfc8621.contract.Fixture.BOB;
import static org.apache.james.jmap.rfc8621.contract.Fixture.getHeadersWith;

import java.util.List;
import java.util.Optional;

import org.apache.james.jmap.core.AccountId;
import org.apache.james.jmap.oidc.JMAPOidcConfiguration;
import org.apache.james.oidc.Aud;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.oidc.OidcAuthenticationStrategy;

public abstract class OidcAuthenticationContract extends org.apache.james.jmap.rfc8621.contract.OidcAuthenticationContract {
    protected static final Optional<List<String>> OIDC_AUTHENTICATION_STRATEGY =
        Optional.of(List.of(OidcAuthenticationStrategy.class.getCanonicalName()));

    protected static JMAPOidcConfiguration oidcConfiguration() {
        return oidcConfiguration(List.of(new Aud("tmail"), new Aud("james")));
    }

    @Override
    protected String primaryAudience() {
        return "tmail";
    }

    @Override
    protected String secondaryAudience() {
        return "james";
    }

    @Test
    void forwardSetShouldSucceedWithOidcAuthenticationStrategy() {
        AccountId accountId = AccountId.from(BOB()).toOption().get();

        String request = """
            {
                "using": [ "urn:ietf:params:jmap:core",
                           "com:linagora:params:jmap:forward" ],
                "methodCalls": [
                  ["Forward/set", {
                    "accountId": "%s",
                    "update": {
                        "singleton": {
                            "localCopy": true,
                            "forwards": [
                                "targetA@domain.org",
                                "targetB@domain.org"
                            ]
                        }
                    }
                  }, "c1"]
                ]
            }""".formatted(accountId.id());

        mockValidToken(BOB().asString());

        String response = given()
            .headers(getHeadersWith(authHeader))
            .body(request)
        .when()
            .post()
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .contentType("application/json")
            .extract()
            .body()
            .asString();

        assertThatJson(response).isEqualTo("""
            {
              "sessionState": "${json-unit.ignore}",
              "methodResponses": [
                ["Forward/set", {
                  "accountId": "%s",
                  "newState": "${json-unit.ignore}",
                  "updated": {"singleton":{}}
                }, "c1"]
              ]
            }
            """.formatted(accountId.id()));
    }
}
