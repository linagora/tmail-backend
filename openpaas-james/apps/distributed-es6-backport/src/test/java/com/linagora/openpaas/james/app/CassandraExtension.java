package com.linagora.openpaas.james.app;

import org.apache.james.GuiceModuleTestExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.inject.Module;

public class CassandraExtension implements GuiceModuleTestExtension {

    private final DockerCassandraRule cassandra;

    public CassandraExtension() {
        this.cassandra = new DockerCassandraRule();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        cassandra.start();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        cassandra.stop();
    }

    @Override
    public Module getModule() {
        return cassandra.getModule();
    }

    public DockerCassandraRule getCassandra() {
        return cassandra;
    }

    public void pause() {
        cassandra.pause();
    }

    public void unpause() {
        cassandra.unpause();
    }
}
