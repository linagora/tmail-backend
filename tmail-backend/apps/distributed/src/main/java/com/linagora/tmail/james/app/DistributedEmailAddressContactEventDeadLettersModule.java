package com.linagora.tmail.james.app;


import org.apache.james.events.CassandraEventDeadLetters;
import org.apache.james.events.CassandraEventDeadLettersDAO;
import org.apache.james.events.CassandraEventDeadLettersGroupDAO;
import org.apache.james.events.EventDeadLetters;

import com.datastax.oss.driver.api.core.CqlSession;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.linagora.tmail.james.jmap.EmailAddressContactInjectKeys;
import com.linagora.tmail.james.jmap.contact.TmailJmapEventSerializer;

public class DistributedEmailAddressContactEventDeadLettersModule extends AbstractModule {

    @Provides
    @Singleton
    @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE)
    EventDeadLetters provideEventDeadLetters(CassandraEventDeadLettersGroupDAO cassandraEventDeadLettersGroupDAO,
                                             CqlSession session,
                                             TmailJmapEventSerializer tmailJmapEventSerializer) {
        return new CassandraEventDeadLetters(new CassandraEventDeadLettersDAO(session, tmailJmapEventSerializer),
            cassandraEventDeadLettersGroupDAO);
    }
}
