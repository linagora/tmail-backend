package com.linagora.tmail.james.app;

import com.google.inject.AbstractModule;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.contact.MinAutoCompleteInputLength;

public class DisabledEmailAddressContactSearchEngineModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(EmailAddressContactSearchEngine.class).to(DisabledEmailAddressContactSearchEngine.class);

        bind(MinAutoCompleteInputLength.class).toInstance(MinAutoCompleteInputLength.ONE());
    }
}
