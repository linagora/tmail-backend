package com.linagora.tmail.carddav;

import org.apache.james.jmap.core.URL;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

public class CardDavServerExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private ClientAndServer mockServer = null;

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        mockServer = ClientAndServer.startClientAndServer(0);
        ConfigurationProperties.logLevel("DEBUG");
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(ClientAndServer.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return mockServer;
    }

    public URL getBaseUrl() {
        return new URL("http://localhost:" + mockServer.getPort());
    }

    public void setCollectedContactExists(String openPassUserId, String collectedContactUid, boolean exists) {
        if (exists) {
            mockServer.when(HttpRequest.request()
                    .withMethod("GET")
                    .withPath("/addressbooks/" + openPassUserId + "/collected/" + collectedContactUid))
                .respond(org.mockserver.model.HttpResponse.response()
                    .withStatusCode(200));
        } else {
            mockServer.when(HttpRequest.request()
                    .withMethod("GET")
                    .withPath("/addressbooks/" + openPassUserId + "/collected/" + collectedContactUid))
                .respond(org.mockserver.model.HttpResponse.response()
                    .withStatusCode(404));
        }
    }

    public void setCreateCollectedContact(String openPassUserId, String collectedContactUid) {
        mockServer.when(HttpRequest.request()
                .withMethod("PUT")
                .withPath("/addressbooks/" + openPassUserId + "/collected/" + collectedContactUid + ".vcf"))
            .respond(org.mockserver.model.HttpResponse.response()
                .withStatusCode(201));
    }

    public void setCreateCollectedContactAlreadyExists(String openPassUserId, String collectedContactUid) {
        mockServer.when(HttpRequest.request()
                .withMethod("PUT")
                .withPath("/addressbooks/" + openPassUserId + "/collected/" + collectedContactUid + ".vcf"))
            .respond(org.mockserver.model.HttpResponse.response()
                .withStatusCode(204));
    }

    public void assertCollectedContactExistsWasCalled(String openPassUserId, String collectedContactUid) {
        mockServer.verify(HttpRequest.request()
            .withMethod("GET")
            .withPath("/addressbooks/" + openPassUserId + "/collected/" + collectedContactUid),


            VerificationTimes.exactly(1));
    }

    public void assertCreateCollectedContactWasCalled(String openPassUserId, String collectedContactUid) {
        mockServer.verify(HttpRequest.request()
            .withMethod("PUT")
            .withPath("/addressbooks/" + openPassUserId + "/collected/" + collectedContactUid + ".vcf"),
            VerificationTimes.exactly(1));
    }
}
