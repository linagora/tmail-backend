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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail;

import static com.linagora.tmail.configuration.OpenPaasConfiguration.OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED;

import java.util.Optional;

import org.apache.http.auth.UsernamePasswordCredentials;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.linagora.tmail.configuration.DavConfiguration;
import com.linagora.tmail.configuration.OpenPaasConfiguration;

public class OpenPaasSetupTestModule extends AbstractModule {
    private static final boolean TRUST_ALL_SSL_CERTS = true;

    @Provides
    @Singleton
    public DockerOpenPaasSetup provideDockerOpenPaasSetup() {
        return DockerOpenPaasSetupSingleton.singleton;
    }

    @Provides
    @Singleton
    public OpenPaasConfiguration provideOpenPaasConfiguration(DockerOpenPaasSetup dockerOpenPaasSetup, DavConfiguration davConfiguration) {
        return new OpenPaasConfiguration(
            dockerOpenPaasSetup.getOpenPaasIpAddress(),
            "admin@open-paas.org",
            "secret",
            TRUST_ALL_SSL_CERTS,
            Optional.of(new OpenPaasConfiguration.ContactConsumerConfiguration(
                ImmutableList.of(AmqpUri.from(dockerOpenPaasSetup.rabbitMqUri())),
                OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED)),
            Optional.of(davConfiguration));
    }

    @Provides
    @Singleton
    public DavConfiguration provideDavConfiguration(DockerOpenPaasSetup dockerOpenPaasSetup) {
        return new DavConfiguration(
            new UsernamePasswordCredentials("admin", "secret123"),
            dockerOpenPaasSetup.getSabreDavURI(),
            Optional.of(TRUST_ALL_SSL_CERTS),
            Optional.empty());
    }

}
