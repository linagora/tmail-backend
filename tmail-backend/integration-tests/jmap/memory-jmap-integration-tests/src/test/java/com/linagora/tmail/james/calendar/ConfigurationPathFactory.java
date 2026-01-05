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

package com.linagora.tmail.james.calendar;

import static com.linagora.tmail.common.TemporaryTmailServerUtils.BASE_CONFIGURATION_FILE_NAMES;

import java.io.File;

import org.apache.james.server.core.configuration.Configuration.ConfigurationPath;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.common.TemporaryTmailServerUtils;

public record ConfigurationPathFactory(TemporaryTmailServerUtils serverUtils) {
    public static ConfigurationPathFactory create(File workingDir) {
        TemporaryTmailServerUtils serverUtils = new TemporaryTmailServerUtils(workingDir, ImmutableList.<String>builder()
            .addAll(BASE_CONFIGURATION_FILE_NAMES)
            .add("mailetcontainer_with_dav_openpaas.xml")
            .add("linagora-ecosystem.properties")
            .add("usersrepository.xml")
            .add("pop3server.xml")
            .build());

        return new ConfigurationPathFactory(serverUtils);
    }

    public ConfigurationPath withCalendarSupport() {
        serverUtils.copyResource("mailetcontainer_with_dav_openpaas.xml", "mailetcontainer.xml");
        return serverUtils.getConfigurationPath();
    }

    public ConfigurationPath withoutCalendarSupport() {
        return serverUtils.getConfigurationPath();
    }
}
