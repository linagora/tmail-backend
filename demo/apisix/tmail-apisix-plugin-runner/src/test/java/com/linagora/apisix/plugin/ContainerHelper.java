package com.linagora.apisix.plugin;

import static java.lang.Boolean.TRUE;

import org.testcontainers.containers.GenericContainer;

public class ContainerHelper {

    public static void pause(GenericContainer container) {
        container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
    }

    public static void unPause(GenericContainer container) {
        if (TRUE.equals(container.getDockerClient().inspectContainerCmd(container.getContainerId())
            .exec()
            .getState()
            .getPaused())) {
            container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
        }
    }
}
