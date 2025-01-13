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

package com.linagora.tmail.james.jmap.model;

import static com.linagora.tmail.james.jmap.OpenSearchContactConfiguration.DEFAULT_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.util.Optional;

import org.apache.james.backends.opensearch.DockerOpenSearchExtension;
import org.apache.james.backends.opensearch.IndexCreationFactory;
import org.apache.james.backends.opensearch.OpenSearchConfiguration;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;

import com.linagora.tmail.james.jmap.ContactMappingFactory;
import com.linagora.tmail.james.jmap.OSEmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngineContract;
import com.linagora.tmail.james.jmap.contact.MatchQuery;
import com.linagora.tmail.james.jmap.contact.QueryType;

public class OSEmailAddressContactSearchTest implements EmailAddressContactSearchEngineContract {
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @RegisterExtension
    public final DockerOpenSearchExtension openSearch = new DockerOpenSearchExtension();

    ReactorOpenSearchClient client;
    OSEmailAddressContactSearchEngine searchEngine;

    @BeforeEach
    void setUp() {
        ContactMappingFactory contactMappingFactory = new ContactMappingFactory(OpenSearchConfiguration.DEFAULT_CONFIGURATION, DEFAULT_CONFIGURATION);
        client = openSearch.getDockerOpenSearch().clientProvider().get();

        createUserContactIndex(client, contactMappingFactory);
        createDomainContactIndex(client, contactMappingFactory);

        searchEngine = new OSEmailAddressContactSearchEngine(client, DEFAULT_CONFIGURATION);
    }

    @Override
    public EmailAddressContactSearchEngine testee() {
        return searchEngine;
    }

    @Override
    public void awaitDocumentsIndexed(QueryType queryType, long documentCount) {
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(client.search(
                    new SearchRequest.Builder()
                        .index(DEFAULT_CONFIGURATION.getUserContactIndexName().getValue(), DEFAULT_CONFIGURATION.getDomainContactIndexName().getValue())
                        .query(extractOpenSearchQuery(queryType))
                        .build())
                .block()
                .hits().total().value()).isEqualTo(documentCount));
    }

    private Query extractOpenSearchQuery(QueryType queryType) {
        if (queryType instanceof MatchQuery matchQuery) {
            return QueryBuilders.match()
                .field((matchQuery.field()))
                .query(new FieldValue.Builder().stringValue(matchQuery.value()).build())
                .build()
                ._toQuery();
        } else {
            return QueryBuilders.matchAll().build()._toQuery();
        }
    }

    private ReactorOpenSearchClient createUserContactIndex(ReactorOpenSearchClient client, ContactMappingFactory contactMappingFactory) {
        return new IndexCreationFactory(OpenSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(DEFAULT_CONFIGURATION.getUserContactIndexName())
            .addAlias(DEFAULT_CONFIGURATION.getUserContactReadAliasName())
            .addAlias(DEFAULT_CONFIGURATION.getUserContactWriteAliasName())
            .createIndexAndAliases(client, Optional.of(contactMappingFactory.generalContactIndicesSetting()), Optional.of(contactMappingFactory.userContactMappingContent()));
    }

    private ReactorOpenSearchClient createDomainContactIndex(ReactorOpenSearchClient client, ContactMappingFactory contactMappingFactory) {
        return new IndexCreationFactory(OpenSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(DEFAULT_CONFIGURATION.getDomainContactIndexName())
            .addAlias(DEFAULT_CONFIGURATION.getDomainContactReadAliasName())
            .addAlias(DEFAULT_CONFIGURATION.getDomainContactWriteAliasName())
            .createIndexAndAliases(client, Optional.of(contactMappingFactory.generalContactIndicesSetting()), Optional.of(contactMappingFactory.domainContactMappingContent()));
    }
}
