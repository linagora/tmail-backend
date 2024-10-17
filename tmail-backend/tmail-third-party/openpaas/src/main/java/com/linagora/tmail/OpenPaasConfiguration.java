package com.linagora.tmail;

import java.net.URL;

public record OpenPaasConfiguration(URL restClientUrl, String restClientUser, String restClientPassword) {
}
