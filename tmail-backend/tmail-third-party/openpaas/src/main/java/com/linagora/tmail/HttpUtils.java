/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.http.auth.UsernamePasswordCredentials;

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

    public static String createBasicAuthenticationToken(UsernamePasswordCredentials credentials) {
        return createBasicAuthenticationToken(credentials.getUserName(), credentials.getPassword());
    }
}
