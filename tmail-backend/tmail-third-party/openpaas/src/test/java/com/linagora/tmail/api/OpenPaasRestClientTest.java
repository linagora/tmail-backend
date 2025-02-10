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

package com.linagora.tmail.api;

import static com.linagora.tmail.configuration.OpenPaasConfiguration.OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED;
import static com.linagora.tmail.configuration.OpenPaasConfiguration.OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_DISABLED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import com.linagora.tmail.dav.WireMockOpenPaaSServerExtension;
import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.AmqpUri;
import com.linagora.tmail.configuration.OpenPaasConfiguration;

public class OpenPaasRestClientTest {
    public static final String BAD_USER_ID = "BAD_ID";
    @RegisterExtension
    static WireMockOpenPaaSServerExtension openPaasServerExtension = new WireMockOpenPaaSServerExtension();

    OpenPaasRestClient restClient;

    @BeforeEach
    void setup() {
        OpenPaasConfiguration openPaasConfig = new OpenPaasConfiguration(
            openPaasServerExtension.getBaseUrl(),
            WireMockOpenPaaSServerExtension.ALICE_ID,
            WireMockOpenPaaSServerExtension.GOOD_PASSWORD,
            OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_DISABLED,
           new OpenPaasConfiguration.ContactConsumerConfiguration(
               ImmutableList.of(AmqpUri.from("amqp://not_important.com")),
                OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED));

        restClient = new OpenPaasRestClient(openPaasConfig);
    }

    @Test
    void shouldReturnUserMailAddressWhenUserIdAndAuthenticationTokenIsCorrect()
        throws AddressException {
        assertThat(restClient.retrieveMailAddress(WireMockOpenPaaSServerExtension.ALICE_ID).block())
            .isEqualTo(new MailAddress(WireMockOpenPaaSServerExtension.ALICE_EMAIL));
    }

    @Test
    void shouldReturnEmptyMonoWhenUserWithIdNotFound() {
        assertThat(restClient.retrieveMailAddress(BAD_USER_ID).blockOptional()).isEmpty();
    }

    @Test
    void shouldThrowExceptionOnErrorStatusCode() throws URISyntaxException {
        OpenPaasConfiguration openPaasConfig = new OpenPaasConfiguration(
            openPaasServerExtension.getBaseUrl(),
            WireMockOpenPaaSServerExtension.ALICE_ID,
            WireMockOpenPaaSServerExtension.BAD_PASSWORD,
            OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_DISABLED,
            new OpenPaasConfiguration.ContactConsumerConfiguration(
                ImmutableList.of(AmqpUri.from("amqp://not_important.com")),
                OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED));

        restClient = new OpenPaasRestClient(openPaasConfig);

        assertThatThrownBy(() -> restClient.retrieveMailAddress(WireMockOpenPaaSServerExtension.ALICE_ID).block())
            .isInstanceOf(OpenPaasRestClientException.class);
    }

    @Test
    void searchOpenPaasUserIdShouldReturnUserIdWhenUserExists() {
        assertThat(restClient.searchOpenPaasUserId(WireMockOpenPaaSServerExtension.ALICE_EMAIL).block())
            .isEqualTo(WireMockOpenPaaSServerExtension.ALICE_ID);
    }

    @Test
    void searchOpenPaasUserIdShouldReturnEmptyWhenSearchResultIsEmpty() {
        assertThat(restClient.searchOpenPaasUserId(WireMockOpenPaaSServerExtension.NOTFOUND_EMAIL).blockOptional())
            .isEmpty();
    }

    @Test
    void searchOpenPaasUserIdShouldReturnEmptyWhenResponseError() {
        assertThat(restClient.searchOpenPaasUserId(WireMockOpenPaaSServerExtension.ERROR_MAIL).blockOptional())
            .isEmpty();
    }
}
