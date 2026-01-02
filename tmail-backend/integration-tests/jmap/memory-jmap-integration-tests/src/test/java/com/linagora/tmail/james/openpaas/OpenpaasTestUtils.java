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

package com.linagora.tmail.james.openpaas;

import static com.linagora.tmail.common.TemporaryTmailServerUtils.BASE_CONFIGURATION_FILE_NAMES;

import java.io.File;

import org.apache.james.server.core.configuration.Configuration;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.common.TemporaryTmailServerUtils;

public class OpenpaasTestUtils {

    public static Configuration.ConfigurationPath setupConfigurationPath(File workingDir, boolean calDavSupport) {
        TemporaryTmailServerUtils serverUtils = new TemporaryTmailServerUtils(workingDir, ImmutableList.<String>builder()
            .addAll(BASE_CONFIGURATION_FILE_NAMES)
            .add("mailetcontainer_with_dav_openpaas.xml")
            .add("linagora-ecosystem.properties")
            .add("usersrepository.xml")
            .add("pop3server.xml")
            .build());

        if (calDavSupport) {
            serverUtils.copyResource("mailetcontainer_with_dav_openpaas.xml", "mailetcontainer.xml");
        }
        return serverUtils.getConfigurationPath();
    }
}
