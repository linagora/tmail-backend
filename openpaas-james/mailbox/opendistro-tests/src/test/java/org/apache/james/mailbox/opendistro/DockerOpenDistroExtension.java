package org.apache.james.mailbox.opendistro;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class DockerOpenDistroExtension implements AfterEachCallback, BeforeEachCallback, ParameterResolver {

    private final DockerOpenDistro openDistro = DockerOpenDistroSingleton.INSTANCE;

    @Override
    public void afterEach(ExtensionContext context) {
        openDistro.cleanUpData();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        if (!openDistro.isRunning()) {
            openDistro.unpause();
        }
        awaitForOpenDistro();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == DockerOpenDistro.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return openDistro;
    }

    public void awaitForOpenDistro() {
        openDistro.flushIndices();
    }

    public DockerOpenDistro getDockerOpenDistro() {
        return openDistro;
    }
}
