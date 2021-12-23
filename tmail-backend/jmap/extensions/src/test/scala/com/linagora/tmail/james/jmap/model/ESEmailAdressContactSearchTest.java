package com.linagora.tmail.james.jmap.model;

import com.linagora.tmail.james.jmap.ESEmailAddressContactSearchEngine;
import org.apache.james.backends.es.v7.DockerElasticSearchExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ESEmailAdressContactSearchTest implements EmailAddressContactSearchEngineContract{

    @RegisterExtension
    public DockerElasticSearchExtension elasticSearch = new DockerElasticSearchExtension();

    @Override
    public EmailAddressContactSearchEngine testee() {
        return new ESEmailAddressContactSearchEngine(elasticSearch.getDockerElasticSearch().clientProvider().get());
    }
}
