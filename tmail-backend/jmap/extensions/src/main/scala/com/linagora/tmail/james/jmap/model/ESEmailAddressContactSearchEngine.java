package com.linagora.tmail.james.jmap.model;

import org.apache.james.jmap.api.model.AccountId;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import scala.runtime.BoxedUnit;

public class ESEmailAddressContactSearchEngine implements EmailAddressContactSearchEngine{
    @Override
    public Publisher<BoxedUnit> index(AccountId accountId, EmailAddressContact contact) {
        //Mono.fromCallable()
        return null;
    }

    @Override
    public Publisher<EmailAddressContact> autoComplete(AccountId accountId, String part) {
        return null;
    }
}
