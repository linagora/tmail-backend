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
package com.linagora.tmail.mailet.conf;

import java.net.URL;
import java.util.Optional;

import com.linagora.tmail.mailet.rag.RagConfig;
import com.linagora.tmail.scribe.ScribeConfiguration;

public record AiHttpClientConfiguration(String authorizationToken,
                                        Optional<URL> baseURLOpt,
                                        boolean trustAllCertificates) {

    public static AiHttpClientConfiguration from(RagConfig ragConfig) {
        return new AiHttpClientConfiguration(ragConfig.getAuthorizationToken(),
            ragConfig.getBaseURLOpt(),
            ragConfig.getTrustAllCertificates());
    }

    public static AiHttpClientConfiguration from(ScribeConfiguration scribeConfig) {
        return new AiHttpClientConfiguration(scribeConfig.authorizationToken(),
            scribeConfig.baseURLOpt(),
            scribeConfig.trustAllCertificates());
    }
}
