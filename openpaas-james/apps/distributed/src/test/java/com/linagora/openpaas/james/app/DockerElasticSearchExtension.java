package com.linagora.openpaas.james.app;

import java.time.Duration;
import java.util.Optional;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.backends.es.v7.DockerElasticSearch;
import org.apache.james.backends.es.v7.DockerElasticSearchSingleton;
import org.apache.james.backends.es.v7.ElasticSearchConfiguration;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.inject.Module;

public class DockerElasticSearchExtension implements GuiceModuleTestExtension {

    private final DockerElasticSearch dockerElasticSearch;
    private Optional<Duration> requestTimeout;

    public DockerElasticSearchExtension() {
        this(DockerElasticSearchSingleton.INSTANCE);
    }

    public DockerElasticSearchExtension withRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = Optional.of(requestTimeout);
        return this;
    }

    public DockerElasticSearchExtension(DockerElasticSearch dockerElasticSearch) {
        this.dockerElasticSearch = dockerElasticSearch;
        requestTimeout = Optional.empty();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        getDockerES().start();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        if (!getDockerES().isRunning()) {
            getDockerES().unpause();
        }
        await();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
    }

    @Override
    public Module getModule() {
        return binder -> binder.bind(ElasticSearchConfiguration.class)
            .toInstance(getElasticSearchConfigurationForDocker());
    }

    @Override
    public void await() {
        getDockerES().flushIndices();
    }

    private ElasticSearchConfiguration getElasticSearchConfigurationForDocker() {
        return ElasticSearchConfiguration.builder()
            .addHost(getDockerES().getHttpHost())
            .requestTimeout(requestTimeout)
            .build();
    }

    public DockerElasticSearch getDockerES() {
        return dockerElasticSearch;
    }
}
