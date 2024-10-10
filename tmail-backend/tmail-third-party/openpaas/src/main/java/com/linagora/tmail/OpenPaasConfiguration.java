package com.linagora.tmail;

import java.net.URL;

public record OpenPaasConfiguration(URL restClientUrl, String restClientUser,
                                       // TODO: Read password as bytes for better security
                                       String restClientPassword) {
}
