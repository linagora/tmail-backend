package org.apache.james.mailbox.opendistro;

public class DockerAuthOpenDistroSingleton {
    public static DockerOpenDistro INSTANCE = new DockerOpenDistro.WithAuth();

    static {
        INSTANCE.start();
    }
}
