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

package com.linagora.tmail.deployment;

import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

class DistributedLdapDeploymentTest {
    @RegisterExtension
    static final TmailDistributedLdapExtension extension = new TmailDistributedLdapExtension();

    @Nested
    class Cli implements CliContract {
        @Override
        public GenericContainer<?> jamesContainer() {
            return extension.getContainer();
        }
    }

    @Nested
    class ImapAndSmtp extends ImapAndSmtpContract {
        @Override
        protected ExternalJamesConfiguration configuration() {
            return extension.configuration();
        }

        @Override
        protected GenericContainer<?> container() {
            return extension.getContainer();
        }
    }

    @Nested
    class Jmap implements JmapContract {
        @Override
        public GenericContainer<?> jmapContainer() {
            return extension.getContainer();
        }
    }
}
