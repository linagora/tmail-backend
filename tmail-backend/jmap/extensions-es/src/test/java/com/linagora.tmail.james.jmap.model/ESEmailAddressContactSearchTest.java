package com.linagora.tmail.james.jmap.model;

import com.linagora.tmail.james.jmap.ESEmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.model.EmailAddressContactSearchEngine;
import org.apache.james.backends.es.v7.DockerElasticSearchExtension;
import org.apache.james.backends.es.v7.ReactorElasticSearchClient;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ESEmailAddressContactSearchTest implements EmailAddressContactSearchEngineContract{

    @RegisterExtension
    public final DockerElasticSearchExtension elasticSearch = new DockerElasticSearchExtension();

    final ReactorElasticSearchClient client = elasticSearch.getDockerElasticSearch().clientProvider().get();

    @Override
    public EmailAddressContactSearchEngine testee() {
        return new ESEmailAddressContactSearchEngine(client);
    }
}
