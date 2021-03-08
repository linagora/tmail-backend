package com.linagora.openpaas.james.common.probe;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.utils.GuiceProbe;

import reactor.core.publisher.Mono;

public class JmapGuiceCustomProbe implements GuiceProbe {
    private final FilteringManagement filteringManagement;

    @Inject
    public JmapGuiceCustomProbe(FilteringManagement filteringManagement) {
        this.filteringManagement = filteringManagement;
    }

    public void setRulesForUser(Username username, Rule... rules) {
        Mono.from(filteringManagement.defineRulesForUser(username, rules)).block();
    }

}

