package com.linagora.tmail;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.ContainerState;

public class DockerOpenPaasExtensionTest {

    @RegisterExtension
    static DockerOpenPaasExtension dockerOpenPaasExtension = new DockerOpenPaasExtension();

    @Test
    void allServersShouldStartSuccessfully() {
       assertTrue(dockerOpenPaasExtension.getDockerOpenPaasSetup().getAllContainers()
           .stream().allMatch(this::checkContainerStarted));
    }

    @Test
    void newTestUserShouldSucceed() {
        OpenPaasUser user = dockerOpenPaasExtension.newTestUser();
        assertAll(
            () -> assertThat("User id should not be null", user.id() != null),
            () -> assertThat("User firstname should not be null", user.firstname() != null),
            () -> assertThat("User lastname should not be null", user.lastname() != null),
            () -> assertThat("User email should not be null", user.email() != null),
            () -> assertThat("User password should not be null", user.password() != null)
        );
    }

    boolean checkContainerStarted(ContainerState containerState) {
        return containerState.isRunning();
    }
}
