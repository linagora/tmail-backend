package com.linagora.tmail;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class HttpUtils {

    /**
     * Returns the AUTHORIZATION header value in order to implement basic authentication.
     *<p>
     * For more information see <a href="https://datatracker.ietf.org/doc/html/">RFC2617#section-2</a>.
     * */
    public static String createBasicAuthenticationToken(String user, String password) {
        String userPassword = user + ":" + password;
        byte[] base64UserPassword = Base64
            .getEncoder()
            .encode(userPassword.getBytes(StandardCharsets.UTF_8));
        return "Basic " + new String(base64UserPassword, StandardCharsets.UTF_8);
    }
}
