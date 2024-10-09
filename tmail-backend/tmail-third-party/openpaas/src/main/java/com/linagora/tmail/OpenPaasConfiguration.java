package com.linagora.tmail;

import java.net.URL;

public class OpenPaasConfiguration {
    private final URL webClientbaseUrl;
    private final String webClientUser;
    private final String webClientPassword;

    // TODO: Read password as bytes for better security
    public OpenPaasConfiguration(URL webClientbaseUrl, String webClientUser,
                                 String webClientPassword) {
        this.webClientbaseUrl = webClientbaseUrl;
        this.webClientUser = webClientUser;
        this.webClientPassword = webClientPassword;
    }

    public URL getWebClientBaseUrl() {
        return webClientbaseUrl;
    }

    public String getWebClientUser() {
        return webClientUser;
    }

    public String getWebClientPassword() {
        return webClientPassword;
    }
}
