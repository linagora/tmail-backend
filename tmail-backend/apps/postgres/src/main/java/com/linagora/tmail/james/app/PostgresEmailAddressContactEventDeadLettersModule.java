package com.linagora.tmail.james.app;

import jakarta.inject.Named;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.PostgresEventDeadLetters;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.linagora.tmail.james.jmap.EmailAddressContactInjectKeys;
import com.linagora.tmail.james.jmap.contact.TmailJmapEventSerializer;

public class PostgresEmailAddressContactEventDeadLettersModule extends AbstractModule {

    @Provides
    @Singleton
    @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE)
    EventDeadLetters provideEventDeadLettersForAutoComplete(PostgresExecutor postgresExecutor, TmailJmapEventSerializer tmailJmapEventSerializer) {
        return new PostgresEventDeadLetters(postgresExecutor, tmailJmapEventSerializer);
    }
}
