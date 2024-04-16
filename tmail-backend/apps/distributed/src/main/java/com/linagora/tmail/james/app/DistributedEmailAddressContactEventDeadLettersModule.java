package com.linagora.tmail.james.app;

import org.apache.james.events.EventDeadLetters;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.linagora.tmail.james.jmap.EmailAddressContactInjectKeys;

public class DistributedEmailAddressContactEventDeadLettersModule extends AbstractModule {

    // TODO: for pass ci on postgresql branch,
    // It should be replaced by https://github.com/linagora/tmail-backend/pull/1016  (when rebase master branch)
    @Provides
    @Singleton
    @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE)
    EventDeadLetters provideEventDeadLetters(EventDeadLetters eventDeadLetters) {
        return eventDeadLetters;
    }
}
