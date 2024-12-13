package com.linagora.tmail.james.app.modules.jmap;

import org.apache.james.events.EventBus;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;
import com.linagora.tmail.james.jmap.EmailAddressContactInjectKeys;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactListener;
import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngineModule;

public class MemoryEmailAddressContactModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new InMemoryEmailAddressContactSearchEngineModule());
    }

    @ProvidesIntoSet
    public InitializationOperation registerListener(
            @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) EventBus eventBus,
            EmailAddressContactListener emailAddressContactListener) {
        return InitilizationOperationBuilder
                .forClass(EmailAddressContactEventLoader.class)
                .init(() -> eventBus.register(emailAddressContactListener));
    }

    @Provides
    @Singleton
    @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE)
    public EventBus provideInVMEventBus(EventBus eventBus) {
        return eventBus;
    }

    public static class EmailAddressContactEventLoader implements Startable {

    }
}


