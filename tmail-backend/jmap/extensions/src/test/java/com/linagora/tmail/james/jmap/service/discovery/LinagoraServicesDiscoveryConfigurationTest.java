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

package com.linagora.tmail.james.jmap.service.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.junit.jupiter.api.Test;

class LinagoraServicesDiscoveryConfigurationTest {
    private final static LinagoraServicesDiscoveryItem LINSHARE = new LinagoraServicesDiscoveryItem("linshareApiUrl", "https://linshare.linagora.com/linshare/webservice");
    private final static LinagoraServicesDiscoveryItem LINTO = new LinagoraServicesDiscoveryItem("linToApiUrl", "https://linto.ai/demo");
    private final static LinagoraServicesDiscoveryItem TWAKE = new LinagoraServicesDiscoveryItem("twakeApiUrl", "https://api.twake.app");
    @Test
    void shouldSucceedCase() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(LINSHARE.key(), LINSHARE.value());
        configuration.addProperty(LINTO.key(), LINTO.value());
        configuration.addProperty(TWAKE.key(), TWAKE.value());

        LinagoraServicesDiscoveryConfiguration servicesDiscoveryConfiguration = LinagoraServicesDiscoveryConfiguration.from(configuration);

        assertThat(servicesDiscoveryConfiguration.getServicesAsJava())
            .containsExactly(LINSHARE, LINTO, TWAKE);
    }
}
