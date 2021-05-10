package org.apache.james.mailbox.opendistro;

public class DockerOpenDistroSingleton {
    public static DockerOpenDistro INSTANCE = new DockerOpenDistro.NoAuth();

    static {
        INSTANCE.start();
    }
}
