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

package com.linagora.tmail.james.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.util.Size;
import org.junit.jupiter.api.Test;

class ShippedSmtpServerConfigurationTest {
    private static final File SHIPPED_SMTP_SERVER = new File("src/main/conf/smtpserver.xml");
    private static final long TWENTY_MEGABYTES = 20971520L;
    private static final int LISTENER_COUNT = 3;

    @Test
    void everyShippedSmtpListenerShouldLimitMessageSizeToTwentyMegabytes() throws ConfigurationException {
        assertThat(new Configurations().xml(SHIPPED_SMTP_SERVER).configurationsAt("smtpserver"))
            .hasSize(LISTENER_COUNT)
            .extracting(ShippedSmtpServerConfigurationTest::maxMessageSizeInBytes)
            .containsOnly(TWENTY_MEGABYTES);
    }

    private static long maxMessageSizeInBytes(HierarchicalConfiguration<ImmutableNode> listener) {
        // Same parsing as James SMTPServer: a unit-less value is in kilobytes
        return Size.parse(listener.getString("maxmessagesize"), Size.Unit.K).asBytes();
    }
}
