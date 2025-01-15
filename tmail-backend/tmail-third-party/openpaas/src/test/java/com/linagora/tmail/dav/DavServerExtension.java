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

package com.linagora.tmail.carddav;

import static org.mockserver.model.NottableString.string;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

import org.apache.http.auth.UsernamePasswordCredentials;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.HttpUtils;
import com.linagora.tmail.configuration.CardDavConfiguration;

public class CardDavServerExtension implements BeforeEachCallback, AfterEachCallback, BeforeAllCallback, AfterAllCallback, ParameterResolver {

    public static final String CARD_DAV_ADMIN = "admin";
    public static final String CARD_DAV_ADMIN_PASSWORD = "secret123";
    public static final Function<String, String> CARD_DAV_ADMIN_WITH_DELEGATED_AUTHORIZATION = openPaasUserName -> HttpUtils.createBasicAuthenticationToken(CARD_DAV_ADMIN + "&" + openPaasUserName, CARD_DAV_ADMIN_PASSWORD);

    private ClientAndServer mockServer = null;

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        mockServer = ClientAndServer.startClientAndServer(0);
        ConfigurationProperties.logLevel("DEBUG");
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        if (mockServer != null) {
            mockServer.reset();
        }
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(ClientAndServer.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return mockServer;
    }

    public URI getBaseUrl() {
        return Throwing.supplier(() -> new URI("http://localhost:" + mockServer.getPort())).get();
    }

    public void setCollectedContactExists(String openPassUserName, String openPassUserId, String collectedContactUid, boolean exists) {
        if (exists) {
            mockServer.when(HttpRequest.request()
                    .withMethod("GET")
                    .withPath("/addressbooks/" + openPassUserId + "/collected/" + collectedContactUid + ".vcf")
                    .withHeader(string("Authorization"), string(CARD_DAV_ADMIN_WITH_DELEGATED_AUTHORIZATION.apply(openPassUserName))))
                .respond(HttpResponse.response()
                    .withStatusCode(200));
        } else {
            mockServer.when(HttpRequest.request()
                    .withMethod("GET")
                    .withPath("/addressbooks/" + openPassUserId + "/collected/" + collectedContactUid + ".vcf")
                    .withHeader(string("Authorization"), string(CARD_DAV_ADMIN_WITH_DELEGATED_AUTHORIZATION.apply(openPassUserName))))
                .respond(HttpResponse.response()
                    .withStatusCode(404));
        }
    }

    public void setCreateCollectedContact(String openPassUserName, String openPassUserId, String collectedContactUid) {
        mockServer.when(HttpRequest.request()
                .withMethod("PUT")
                .withPath("/addressbooks/" + openPassUserId + "/collected/" + collectedContactUid + ".vcf")
                .withHeader(string("Content-Type"), string("text/vcard"))
                .withHeader(string("Authorization"), string(CARD_DAV_ADMIN_WITH_DELEGATED_AUTHORIZATION.apply(openPassUserName))))
            .respond(HttpResponse.response()
                .withStatusCode(201));
    }

    public void setCreateCollectedContactAlreadyExists(String openPassUserName, String openPassUserId, String collectedContactUid) {
        mockServer.when(HttpRequest.request()
                .withMethod("PUT")
                .withPath("/addressbooks/" + openPassUserId + "/collected/" + collectedContactUid + ".vcf")
                .withHeader(string("Content-Type"), string("text/vcard"))
                .withHeader(string("Authorization"), string(CARD_DAV_ADMIN_WITH_DELEGATED_AUTHORIZATION.apply(openPassUserName))))
            .respond(HttpResponse.response()
                .withStatusCode(204));
    }

    public void assertCollectedContactExistsWasCalled(String openPassUserName, String openPassUserId, String collectedContactUid, int times) {
        mockServer.verify(HttpRequest.request()
                .withMethod("GET")
                .withPath("/addressbooks/" + openPassUserId + "/collected/" + collectedContactUid)
                .withHeader(string("Authorization"), string(CARD_DAV_ADMIN_WITH_DELEGATED_AUTHORIZATION.apply(openPassUserName))),
            VerificationTimes.exactly(times));
    }

    public void assertCreateCollectedContactWasCalled(String openPassUserName, String openPassUserId, String collectedContactUid, int times) {
        mockServer.verify(HttpRequest.request()
                .withMethod("PUT")
                .withPath("/addressbooks/" + openPassUserId + "/collected/" + collectedContactUid + ".vcf")
                .withHeader(string("Authorization"), string(CARD_DAV_ADMIN_WITH_DELEGATED_AUTHORIZATION.apply(openPassUserName)))
                .withHeader(string("Content-Type"), string("text/vcard")),
            VerificationTimes.exactly(times));
    }

    public CardDavConfiguration getCardDavConfiguration() {
        return new CardDavConfiguration(
            new UsernamePasswordCredentials(CARD_DAV_ADMIN, CARD_DAV_ADMIN_PASSWORD),
            getBaseUrl(),
            Optional.of(true),
            Optional.of(Duration.ofSeconds(10)));
    }
}
