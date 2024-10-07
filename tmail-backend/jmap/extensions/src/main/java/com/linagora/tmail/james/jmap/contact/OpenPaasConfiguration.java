package com.linagora.tmail.james.jmap.contact;

import java.net.URL;

public class OpenPaasConfiguration {
    private final URL webClientbaseUrl;
    private final String webClientUser;
    private final String webClientPassword;

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
