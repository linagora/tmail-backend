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

package com.linagora.tmail.integration.postgres;

import static com.linagora.tmail.james.jmap.OpenSearchContactConfiguration.DEFAULT_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;

import org.apache.james.JamesServerExtension;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.utils.GuiceProbe;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;

import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import com.linagora.tmail.integration.UserDeletionIntegrationContract;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.PostgresEncryptedMailboxModule;
import com.linagora.tmail.james.common.module.JmapGuiceKeystoreManagerModule;
import com.linagora.tmail.james.common.probe.JmapGuiceKeystoreManagerProbe;

@Disabled("TODO https://github.com/linagora/james-project/issues/5143")
public class PostgresUserDeletionIntegrationTest extends UserDeletionIntegrationContract {
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @RegisterExtension
    static DockerOpenSearchExtension opensearchExtension = new DockerOpenSearchExtension();

    @RegisterExtension
    static JamesServerExtension testExtension = PostgresWebAdminBase.JAMES_SERVER_EXTENSION_FUNCTION
        .apply(Modules.combine(new JmapGuiceKeystoreManagerModule(), new PostgresEncryptedMailboxModule(),
            binder -> Multibinder.newSetBinder(binder, GuiceProbe .class).addBinding().to(JmapGuiceKeystoreManagerProbe .class)))
        .extensions(opensearchExtension)
        .build();

    private final ReactorOpenSearchClient client = opensearchExtension.getDockerOS().clientProvider().get();

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Override
    public void awaitDocumentsIndexed(Long documentCount) {
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(client.search(
                    new SearchRequest.Builder()
                        .index(DEFAULT_CONFIGURATION.getUserContactIndexName().getValue(), DEFAULT_CONFIGURATION.getDomainContactIndexName().getValue())
                        .query(QueryBuilders.matchAll().build().toQuery())
                        .build())
                .block()
                .hits().total().value()).isEqualTo(documentCount));
    }

}
