package org.apache.james.mailbox.opendistro;

import java.time.Duration;
import java.util.Optional;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.backends.es.v7.ElasticSearchConfiguration;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.google.inject.Module;

public class DockerOpenDistroExtension implements AfterEachCallback, BeforeEachCallback, ParameterResolver, GuiceModuleTestExtension {

    private final DockerOpenDistro openDistro;
    private Optional<Duration> requestTimeout;

    public DockerOpenDistroExtension(DockerOpenDistro openDistro) {
        this.openDistro = openDistro;
        requestTimeout = Optional.empty();
    }

    public DockerOpenDistroExtension withRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = Optional.of(requestTimeout);
        return this;
    }

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

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        getDockerOpenDistro().start();
    }

    @Override
    public Module getModule() {
        return binder -> binder.bind(ElasticSearchConfiguration.class)
            .toInstance(getOpenDistroConfigurationForDocker());
    }

    private ElasticSearchConfiguration getOpenDistroConfigurationForDocker() {
        return ElasticSearchConfiguration.builder()
            .addHost(getDockerOpenDistro().getHttpHost())
            .requestTimeout(requestTimeout)
            .build();
    }

    public void awaitForOpenDistro() {
        openDistro.flushIndices();
    }

    public DockerOpenDistro getDockerOpenDistro() {
        return openDistro;
    }
}
