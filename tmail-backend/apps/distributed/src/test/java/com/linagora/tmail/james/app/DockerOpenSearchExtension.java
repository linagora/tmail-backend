package com.linagora.tmail.james.app;

import java.time.Duration;
import java.util.Optional;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.backends.opensearch.DockerOpenSearch;
import org.apache.james.backends.opensearch.DockerOpenSearchSingleton;
import org.apache.james.backends.opensearch.OpenSearchConfiguration;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.inject.Module;

public class DockerOpenSearchExtension implements GuiceModuleTestExtension {

    private final DockerOpenSearch dockerOpenSearch;
    private Optional<Duration> requestTimeout;

    public DockerOpenSearchExtension() {
        this(DockerOpenSearchSingleton.INSTANCE);
    }

    public DockerOpenSearchExtension withRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = Optional.of(requestTimeout);
        return this;
    }

    public DockerOpenSearchExtension(DockerOpenSearch dockerOpenSearch) {
        this.dockerOpenSearch = dockerOpenSearch;
        requestTimeout = Optional.empty();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        getDockerOS().start();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        if (!getDockerOS().isRunning()) {
            getDockerOS().unpause();
        }
        await();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
    }

    @Override
    public Module getModule() {
        return binder -> binder.bind(OpenSearchConfiguration.class)
            .toInstance(getOpenSearchConfigurationForDocker());
    }

    @Override
    public void await() {
        getDockerOS().flushIndices();
    }

    private OpenSearchConfiguration getOpenSearchConfigurationForDocker() {
        return OpenSearchConfiguration.builder()
            .addHost(getDockerOS().getHttpHost())
            .requestTimeout(requestTimeout)
            .build();
    }

    public DockerOpenSearch getDockerOS() {
        return dockerOpenSearch;
    }
}
