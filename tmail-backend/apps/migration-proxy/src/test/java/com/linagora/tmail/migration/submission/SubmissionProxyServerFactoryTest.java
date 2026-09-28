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


package com.linagora.tmail.migration.submission;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.lib.handler.ProtocolHandlerLoader;
import org.apache.james.smtpserver.CoreCmdHandlerLoader;
import org.junit.jupiter.api.Test;

class SubmissionProxyServerFactoryTest {
    @Test
    void theMtaCommandSetShouldNeverBeReachableOnSubmissionListeners() {
        SubmissionProxyServerFactory factory = new SubmissionProxyServerFactory(mock(DNSService.class),
            mock(ProtocolHandlerLoader.class), mock(FileSystem.class), new RecordingMetricFactory());
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("smtpserver.bind", "127.0.0.1:0");
        configuration.addProperty("smtpserver.handlerchain[@coreHandlersPackage]", CoreCmdHandlerLoader.class.getName());

        assertThatThrownBy(() -> factory.createServers(configuration))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining(CoreCmdHandlerLoader.class.getName());
    }
}
