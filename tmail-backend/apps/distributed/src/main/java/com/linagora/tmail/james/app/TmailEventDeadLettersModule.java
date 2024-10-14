package com.linagora.tmail.james.app;

import jakarta.inject.Named;

import org.apache.james.events.CassandraEventDeadLetters;
import org.apache.james.events.CassandraEventDeadLettersDAO;
import org.apache.james.events.CassandraEventDeadLettersGroupDAO;
import org.apache.james.events.EventDeadLetters;

import com.datastax.oss.driver.api.core.CqlSession;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.linagora.tmail.common.event.TmailInjectNameConstants;
import com.linagora.tmail.event.TmailEventSerializer;

public class TmailEventDeadLettersModule extends AbstractModule {

    @Provides
    @Singleton
    @Named(TmailInjectNameConstants.TMAIL_EVENT_BUS_INJECT_NAME)
    EventDeadLetters provideEventDeadLetters(CassandraEventDeadLettersGroupDAO cassandraEventDeadLettersGroupDAO,
                                             CqlSession session,
                                             TmailEventSerializer tmailEventSerializer) {
        return new CassandraEventDeadLetters(new CassandraEventDeadLettersDAO(session, tmailEventSerializer),
            cassandraEventDeadLettersGroupDAO);
    }
}
